package com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.candidate

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.candidate.LoadMerchantSettlementCandidates
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementLineSourceKind
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementCandidateFact
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.currentReviewEligibility
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationConfirmationFact
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationItem
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationRun
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDifferenceType
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "LoadMerchantSettlementCandidates",
    packageName = "merchant_settlement.candidate",
    description = "Project eligible and excluded current-effective-run facts for a merchant settlement period",
    aggregates = ["MerchantSettlement"],
    family = "capability-handler"
)
class LoadMerchantSettlementCandidatesHandler(
    private val entityManager: EntityManager,
) : CapabilityHandler<LoadMerchantSettlementCandidates.Request, LoadMerchantSettlementCandidates.Response> {

    /**
     * 只读取 current effective reconciliation run，并在候选时重新核对 Payment 当前 review eligibility。
     * 未决交易按交易粒度排除，旧 run、陈旧 settlementBlocked 摘要或 confirmation 都不能绕过当前复核阻断。
     */
    override fun call(request: LoadMerchantSettlementCandidates.Request): LoadMerchantSettlementCandidates.Response {
        val zone = ZoneId.of(request.businessTimezone)
        val startDate = request.periodStart.atZone(zone).toLocalDate()
        val endDate = request.periodEnd.minusNanos(1).atZone(zone).toLocalDate()
        val batches = entityManager.createQuery(
            "select distinct b from ReconciliationBatch b where b.currency = :currency and b.reconciliationDate between :startDate and :endDate",
            ReconciliationBatch::class.java,
        )
            .setParameter("currency", request.currency.uppercase())
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .resultList

        val facts = mutableListOf<SettlementCandidateFact>()
        val excludedFacts = mutableListOf<SettlementCandidateFact>()
        val blockers = mutableListOf<String>()
        batches.forEach { batch ->
            val currentRun = currentRun(batch)
            if (currentRun == null) {
                blockers += "对账批次 ${batch.id} 没有当前有效运行"
                return@forEach
            }
            currentRun.reconciliationItems.forEach { item ->
                if (item.settlementBlocked || !item.resolved) {
                    blockers += "${item.differenceIdentity}：${item.differenceType.name} 仍阻断结算"
                    excludedFact(request, batch, currentRun, item, "UNRESOLVED_RECONCILIATION")?.let(excludedFacts::add)
                    return@forEach
                }
                val confirmations = item.reconciliationConfirmationFacts.toList()
                if (confirmations.isNotEmpty()) {
                    confirmations.forEach { confirmation ->
                        val projected = projectConfirmation(request, batch, currentRun, item, confirmation, blockers)
                        if (projected == null) {
                            excludedFact(request, batch, currentRun, item, "CONFIRMATION_INELIGIBLE", confirmation)
                                ?.let(excludedFacts::add)
                        } else {
                            facts += projected
                        }
                    }
                    return@forEach
                }
                if (item.differenceType != ReconciliationDifferenceType.MATCHED) {
                    blockers += "${item.differenceIdentity}：差异虽已处置，但没有确认事实"
                    excludedFact(request, batch, currentRun, item, "CONFIRMATION_REQUIRED")?.let(excludedFacts::add)
                    return@forEach
                }
                val projected = projectMatched(request, batch, currentRun, item, blockers)
                if (projected == null) {
                    val paymentReviewBlocked = item.paymentId
                        ?.let { entityManager.find(Payment::class.java, it) }
                        ?.currentReviewEligibility()?.settlementEligible == false
                    val reason = if (paymentReviewBlocked) "PAYMENT_REVIEW_BLOCKED" else "SOURCE_INELIGIBLE"
                    excludedFact(request, batch, currentRun, item, reason)?.let(excludedFacts::add)
                } else {
                    facts += projected
                }
            }
        }
        excludedFacts += unknownCandidates(request)
        val consumedFacts = consumedSourceFactIdentities(request, facts)
        val alreadyConsumed = facts.filter { it.sourceFactIdentity in consumedFacts }.map {
            it.copy(decision = "EXCLUDED", reasonCode = "ALREADY_SETTLED")
        }
        excludedFacts += alreadyConsumed
        val excluded = excludedFacts.distinctBy { it.sourceKind to it.sourceFactIdentity }
        val excludedKeys = excluded.map { it.sourceKind to it.sourceFactIdentity }.toSet()
        return LoadMerchantSettlementCandidates.Response(
            eligibleFacts = facts.filterNot { it.sourceKind to it.sourceFactIdentity in excludedKeys }
                .distinctBy { it.sourceKind to it.sourceFactIdentity }
                .sortedWith(compareBy({ it.occurredAt }, { it.sourceFactIdentity })),
            excludedFacts = excluded.sortedWith(compareBy({ it.occurredAt }, { it.sourceFactIdentity })),
            excludedCount = excluded.size,
            blockerSummaries = blockers.distinct().sorted(),
        )
    }

    private fun consumedSourceFactIdentities(
        request: LoadMerchantSettlementCandidates.Request,
        facts: List<SettlementCandidateFact>,
    ): Set<String> {
        if (facts.isEmpty()) return emptySet()
        val sql = buildString {
            append("select source_fact_identity from settlement_line ")
            append("where effective_consumption_identity is not null ")
            append("and source_fact_identity in (:identities)")
            if (request.predecessorSettlementId != null) {
                append(" and merchant_settlement_id <> :predecessorSettlementId")
            }
        }
        val query = entityManager.createNativeQuery(sql)
            .setParameter("identities", facts.map { it.sourceFactIdentity })
        request.predecessorSettlementId?.let { query.setParameter("predecessorSettlementId", it) }
        return query.resultList.map { it.toString() }.toSet()
    }

    private fun unknownCandidates(request: LoadMerchantSettlementCandidates.Request): List<SettlementCandidateFact> {
        val payments = entityManager.createQuery(
            "select payment from Payment payment where payment.merchantId = :merchantId and payment.currency = :currency and payment.status = :status",
            Payment::class.java,
        ).setParameter("merchantId", request.merchantId)
            .setParameter("currency", request.currency.uppercase())
            .setParameter("status", PaymentStatus.RESULT_UNKNOWN)
            .resultList.mapNotNull { payment ->
                val attempt = payment.attempts.lastOrNull() ?: return@mapNotNull null
                val occurred = attempt.initiatedAt.toInstant(ZoneOffset.UTC)
                if (!within(occurred, request.periodStart, request.periodEnd)) return@mapNotNull null
                SettlementCandidateFact(
                    sourceKind = SettlementLineSourceKind.PAYMENT,
                    transactionKind = ReconciliationTransactionKind.PAYMENT,
                    sourceFactIdentity = "PAYMENT:${payment.id}",
                    feeFactIdentity = null,
                    merchantId = payment.merchantId,
                    channelId = attempt.channelId,
                    currency = payment.currency,
                    paymentId = payment.id,
                    paymentAttemptId = attempt.id.toString(),
                    refundId = null,
                    refundAttemptId = null,
                    reconciliationBatchId = null,
                    reconciliationRunId = null,
                    reconciliationItemId = null,
                    reconciliationConfirmationFactId = null,
                    externalTransactionIdentity = attempt.channelTransactionId ?: attempt.requestIdentity,
                    grossAmount = payment.amount,
                    feeAmount = BigDecimal.ZERO,
                    signedNetAmount = BigDecimal.ZERO,
                    occurredAt = occurred,
                    recordedAt = occurred,
                    feeBasisPoints = null,
                    feeFixedAmount = null,
                    feeRoundingMode = null,
                    feeCurrencyPrecision = null,
                    feeCalculationAmount = null,
                    eligibilityBasis = "PAYMENT_RESULT_UNKNOWN",
                    confirmationReason = null,
                    confirmationEvidence = null,
                    adjustmentSourceIdentity = null,
                    adjustmentEvidence = null,
                    decision = "EXCLUDED",
                    reasonCode = "PAYMENT_RESULT_UNKNOWN",
                )
            }
        val refunds = entityManager.createQuery(
            "select refund from Refund refund where refund.merchantId = :merchantId and refund.currency = :currency and refund.status in :statuses",
            Refund::class.java,
        ).setParameter("merchantId", request.merchantId)
            .setParameter("currency", request.currency.uppercase())
            .setParameter("statuses", listOf(RefundStatus.RESULT_UNKNOWN, RefundStatus.REVIEW_REQUIRED))
            .resultList.mapNotNull { refund ->
                val attempt = refund.attempts.lastOrNull()
                val occurred = (attempt?.initiatedAt ?: refund.requestedAt).toInstant(ZoneOffset.UTC)
                if (!within(occurred, request.periodStart, request.periodEnd)) return@mapNotNull null
                SettlementCandidateFact(
                    sourceKind = SettlementLineSourceKind.REFUND,
                    transactionKind = ReconciliationTransactionKind.REFUND,
                    sourceFactIdentity = "REFUND:${refund.id}",
                    feeFactIdentity = null,
                    merchantId = refund.merchantId,
                    channelId = refund.channelId ?: "REFERENCE_UNASSIGNED",
                    currency = refund.currency,
                    paymentId = refund.paymentId,
                    paymentAttemptId = null,
                    refundId = refund.id,
                    refundAttemptId = attempt?.id?.toString(),
                    reconciliationBatchId = null,
                    reconciliationRunId = null,
                    reconciliationItemId = null,
                    reconciliationConfirmationFactId = null,
                    externalTransactionIdentity = refund.channelRefundId ?: refund.merchantRefundNumber,
                    grossAmount = refund.amount,
                    feeAmount = BigDecimal.ZERO,
                    signedNetAmount = BigDecimal.ZERO,
                    occurredAt = occurred,
                    recordedAt = occurred,
                    feeBasisPoints = null,
                    feeFixedAmount = null,
                    feeRoundingMode = null,
                    feeCurrencyPrecision = null,
                    feeCalculationAmount = null,
                    eligibilityBasis = "REFUND_RESULT_UNKNOWN",
                    confirmationReason = null,
                    confirmationEvidence = null,
                    adjustmentSourceIdentity = null,
                    adjustmentEvidence = null,
                    decision = "EXCLUDED",
                    reasonCode = "REFUND_RESULT_UNKNOWN",
                )
            }
        return payments + refunds
    }

    private fun excludedFact(
        request: LoadMerchantSettlementCandidates.Request,
        batch: ReconciliationBatch,
        run: ReconciliationRun,
        item: ReconciliationItem,
        reasonCode: String,
        confirmation: ReconciliationConfirmationFact? = null,
    ): SettlementCandidateFact? {
        val payment = (confirmation?.paymentId ?: item.paymentId)?.let { entityManager.find(Payment::class.java, it) }
        val refund = (confirmation?.refundId ?: item.refundId)?.let { entityManager.find(Refund::class.java, it) }
        val merchantId = confirmation?.merchantId ?: payment?.merchantId ?: refund?.merchantId ?: return null
        if (merchantId != request.merchantId) return null
        val sourceKind = when {
            confirmation != null -> SettlementLineSourceKind.RECONCILIATION_CONFIRMATION
            item.transactionKind == ReconciliationTransactionKind.PAYMENT -> SettlementLineSourceKind.PAYMENT
            else -> SettlementLineSourceKind.REFUND
        }
        val sourceIdentity = when {
            confirmation != null -> "RECONCILIATION_CONFIRMATION:${confirmation.id}"
            payment != null -> "PAYMENT:${payment.id}"
            refund != null -> "REFUND:${refund.id}"
            else -> "RECONCILIATION_ITEM:${item.id}"
        }
        val occurredAt = (confirmation?.confirmedAt ?: item.platformOccurredAt ?: item.channelOccurredAt)
            ?.toInstant(ZoneOffset.UTC) ?: request.periodStart
        val recordedAt = (confirmation?.updatedAt ?: item.platformRecordedAt ?: item.channelReceivedAt)
            ?.toInstant(ZoneOffset.UTC) ?: request.periodStart
        return SettlementCandidateFact(
            sourceKind = sourceKind,
            transactionKind = item.transactionKind,
            sourceFactIdentity = sourceIdentity,
            feeFactIdentity = payment?.settlementFeeFactIdentity,
            merchantId = merchantId,
            channelId = batch.channelId,
            currency = request.currency.uppercase(),
            paymentId = confirmation?.paymentId ?: item.paymentId,
            paymentAttemptId = item.paymentAttemptId,
            refundId = confirmation?.refundId ?: item.refundId,
            refundAttemptId = item.refundAttemptId,
            reconciliationBatchId = batch.id,
            reconciliationRunId = run.id.toString(),
            reconciliationItemId = item.id.toString(),
            reconciliationConfirmationFactId = confirmation?.id?.toString(),
            externalTransactionIdentity = confirmation?.externalTransactionIdentity ?: item.channelTransactionIdentity
                ?: item.platformTransactionIdentity ?: item.differenceIdentity,
            grossAmount = confirmation?.amount ?: item.platformAmount ?: item.channelAmount ?: BigDecimal.ZERO,
            feeAmount = BigDecimal.ZERO,
            signedNetAmount = BigDecimal.ZERO,
            occurredAt = occurredAt,
            recordedAt = recordedAt,
            feeBasisPoints = payment?.settlementFeeBasisPoints,
            feeFixedAmount = payment?.settlementFixedFeeAmount,
            feeRoundingMode = payment?.settlementFeeRoundingMode,
            feeCurrencyPrecision = payment?.settlementFeeCurrencyPrecision,
            feeCalculationAmount = payment?.settlementFeeCalculationAmount,
            eligibilityBasis = "${item.differenceType.name}:${item.differenceIdentity}",
            confirmationReason = confirmation?.confirmationReason,
            confirmationEvidence = confirmation?.evidence,
            adjustmentSourceIdentity = null,
            adjustmentEvidence = null,
            decision = "EXCLUDED",
            reasonCode = reasonCode,
        )
    }

    private fun currentRun(batch: ReconciliationBatch): ReconciliationRun? {
        val currentId = batch.currentEffectiveRunId ?: return null
        return batch.reconciliationRuns.firstOrNull { it.id.toString() == currentId }
    }

    private fun projectMatched(
        request: LoadMerchantSettlementCandidates.Request,
        batch: ReconciliationBatch,
        run: ReconciliationRun,
        item: ReconciliationItem,
        blockers: MutableList<String>,
    ): SettlementCandidateFact? = when (item.transactionKind) {
        ReconciliationTransactionKind.PAYMENT -> {
            val paymentId = item.paymentId ?: return blocked(blockers, item, "已匹配支付缺少 paymentId")
            val payment = entityManager.find(Payment::class.java, paymentId)
                ?: return blocked(blockers, item, "未找到支付单 $paymentId")
            val reviewEligibility = payment.currentReviewEligibility()
            if (!reviewEligibility.settlementEligible) {
                blocked(
                    blockers,
                    item,
                    "支付单 $paymentId 仍有未解决复核 ${reviewEligibility.blockingReviewIdentities.joinToString(",")}",
                )
            } else {
                paymentFact(request, batch, run, item, payment)
                    ?: blocked(blockers, item, "支付单 $paymentId 缺少商户、渠道或手续费资格证据")
            }
        }
        ReconciliationTransactionKind.REFUND -> {
            val refundId = item.refundId ?: return blocked(blockers, item, "已匹配退款缺少 refundId")
            val refund = entityManager.find(Refund::class.java, refundId)
                ?: return blocked(blockers, item, "未找到退款单 $refundId")
            refundFact(request, batch, run, item, refund)
                ?: blocked(blockers, item, "退款单 $refundId 缺少商户或渠道资格证据")
        }
    }

    private fun projectConfirmation(
        request: LoadMerchantSettlementCandidates.Request,
        batch: ReconciliationBatch,
        run: ReconciliationRun,
        item: ReconciliationItem,
        confirmation: ReconciliationConfirmationFact,
        blockers: MutableList<String>,
    ): SettlementCandidateFact? {
        if (confirmation.merchantId != request.merchantId ||
            confirmation.currency != request.currency.uppercase()) {
            return blocked(blockers, item, "确认事实 ${confirmation.id} 的归属与结算范围不一致")
        }
        val payment = confirmation.paymentId?.let { entityManager.find(Payment::class.java, it) }
        val refund = confirmation.refundId?.let { entityManager.find(Refund::class.java, it) }
        if (confirmation.transactionKind == ReconciliationTransactionKind.PAYMENT && payment == null) {
            return blocked(blockers, item, "支付确认事实 ${confirmation.id} 缺少冻结的支付手续费事实")
        }
        if (payment != null && payment.merchantId != request.merchantId) {
            return blocked(blockers, item, "确认事实引用的支付商户归属不一致")
        }
        if (payment != null) {
            val eligibility = payment.currentReviewEligibility()
            if (!eligibility.settlementEligible) {
                return blocked(
                    blockers,
                    item,
                    "支付单 ${payment.id} 仍有未解决复核 ${eligibility.blockingReviewIdentities.joinToString(",")}",
                )
            }
        }
        if (refund != null && refund.merchantId != request.merchantId) {
            return blocked(blockers, item, "确认事实引用的退款归属不一致")
        }
        val occurredAt = confirmation.confirmedAt.toInstant(ZoneOffset.UTC)
        if (!within(occurredAt, request.periodStart, request.periodEnd)) {
            return blocked(blockers, item, "确认事实 ${confirmation.id} 不在结算周期内")
        }
        val signed = if (confirmation.transactionKind == ReconciliationTransactionKind.REFUND) {
            confirmation.amount.negate()
        } else {
            confirmation.amount
        }
        return SettlementCandidateFact(
            sourceKind = SettlementLineSourceKind.ADJUSTMENT,
            transactionKind = confirmation.transactionKind,
            sourceFactIdentity = "ADJUSTMENT:${confirmation.id}",
            feeFactIdentity = null,
            merchantId = confirmation.merchantId,
            channelId = confirmation.channelId,
            currency = confirmation.currency,
            paymentId = confirmation.paymentId,
            paymentAttemptId = item.paymentAttemptId,
            refundId = confirmation.refundId,
            refundAttemptId = item.refundAttemptId,
            reconciliationBatchId = batch.id,
            reconciliationRunId = run.id.toString(),
            reconciliationItemId = item.id.toString(),
            reconciliationConfirmationFactId = confirmation.id.toString(),
            externalTransactionIdentity = confirmation.externalTransactionIdentity,
            grossAmount = confirmation.amount,
            feeAmount = BigDecimal.ZERO,
            signedNetAmount = signed,
            occurredAt = occurredAt,
            recordedAt = (confirmation.updatedAt ?: confirmation.confirmedAt).toInstant(ZoneOffset.UTC),
            feeBasisPoints = null,
            feeFixedAmount = null,
            feeRoundingMode = null,
            feeCurrencyPrecision = null,
            feeCalculationAmount = null,
            eligibilityBasis = "AUTHORIZED_ADJUSTMENT_CONFIRMATION",
            confirmationReason = confirmation.confirmationReason,
            confirmationEvidence = confirmation.evidence,
            adjustmentSourceIdentity = confirmation.id.toString(),
            adjustmentEvidence = confirmation.evidence,
        )
    }

    private fun paymentFact(
        request: LoadMerchantSettlementCandidates.Request,
        batch: ReconciliationBatch,
        run: ReconciliationRun,
        item: ReconciliationItem,
        payment: Payment,
    ): SettlementCandidateFact? {
        if (payment.status != PaymentStatus.SUCCEEDED || payment.merchantId != request.merchantId ||
            payment.currency != request.currency.uppercase()) return null
        if (!payment.currentReviewEligibility().settlementEligible) return null
        val attempt = payment.attempts.firstOrNull { it.id.toString() == item.paymentAttemptId }
            ?: return null
        val occurred = payment.succeededAt ?: return null
        val feeIdentity = payment.settlementFeeFactIdentity ?: return null
        val fee = payment.settlementFeeAmount ?: return null
        if (payment.settlementFeeRate == null || payment.settlementFeeBasisPoints == null || payment.settlementFixedFeeAmount == null ||
            payment.settlementFeeRoundingMode == null || payment.settlementFeeCurrencyPrecision == null ||
            payment.settlementFeeCalculationAmount == null || payment.settlementFeeFormedAt == null) return null
        val occurredAt = occurred.toInstant(ZoneOffset.UTC)
        if (!within(occurredAt, request.periodStart, request.periodEnd)) return null
        return SettlementCandidateFact(
            sourceKind = SettlementLineSourceKind.PAYMENT,
            transactionKind = ReconciliationTransactionKind.PAYMENT,
            sourceFactIdentity = "PAYMENT:${payment.id}",
            feeFactIdentity = feeIdentity,
            merchantId = payment.merchantId,
            channelId = attempt.channelId,
            currency = payment.currency,
            paymentId = payment.id,
            paymentAttemptId = attempt.id.toString(),
            refundId = null,
            refundAttemptId = null,
            reconciliationBatchId = batch.id,
            reconciliationRunId = run.id.toString(),
            reconciliationItemId = item.id.toString(),
            reconciliationConfirmationFactId = null,
            externalTransactionIdentity = item.channelTransactionIdentity ?: payment.channelTransactionId ?: return null,
            grossAmount = payment.amount,
            feeAmount = fee,
            signedNetAmount = payment.amount - fee,
            occurredAt = occurredAt,
            recordedAt = (payment.updatedAt ?: occurred).toInstant(ZoneOffset.UTC),
            feeBasisPoints = payment.settlementFeeBasisPoints,
            feeFixedAmount = payment.settlementFixedFeeAmount,
            feeRoundingMode = payment.settlementFeeRoundingMode,
            feeCurrencyPrecision = payment.settlementFeeCurrencyPrecision,
            feeCalculationAmount = payment.settlementFeeCalculationAmount,
            eligibilityBasis = "CURRENT_EFFECTIVE_RECONCILIATION_MATCH",
            confirmationReason = null,
            confirmationEvidence = null,
            adjustmentSourceIdentity = null,
            adjustmentEvidence = null,
        )
    }

    private fun refundFact(
        request: LoadMerchantSettlementCandidates.Request,
        batch: ReconciliationBatch,
        run: ReconciliationRun,
        item: ReconciliationItem,
        refund: Refund,
    ): SettlementCandidateFact? {
        if (refund.status != RefundStatus.SUCCEEDED || refund.merchantId != request.merchantId ||
            refund.currency != request.currency.uppercase()) return null
        val attempt = refund.attempts.firstOrNull { it.id.toString() == item.refundAttemptId } ?: return null
        val occurred = refund.finalizedAt ?: attempt.resultOccurredAt ?: return null
        val occurredAt = occurred.toInstant(ZoneOffset.UTC)
        if (!within(occurredAt, request.periodStart, request.periodEnd)) return null
        return SettlementCandidateFact(
            sourceKind = SettlementLineSourceKind.REFUND,
            transactionKind = ReconciliationTransactionKind.REFUND,
            sourceFactIdentity = "REFUND:${refund.id}",
            feeFactIdentity = null,
            merchantId = refund.merchantId,
            channelId = requireNotNull(refund.channelId),
            currency = refund.currency,
            paymentId = refund.paymentId,
            paymentAttemptId = item.paymentAttemptId,
            refundId = refund.id,
            refundAttemptId = attempt.id.toString(),
            reconciliationBatchId = batch.id,
            reconciliationRunId = run.id.toString(),
            reconciliationItemId = item.id.toString(),
            reconciliationConfirmationFactId = null,
            externalTransactionIdentity = item.channelTransactionIdentity ?: refund.channelRefundId ?: return null,
            grossAmount = refund.amount,
            feeAmount = BigDecimal.ZERO,
            signedNetAmount = refund.amount.negate(),
            occurredAt = occurredAt,
            recordedAt = (refund.updatedAt ?: occurred).toInstant(ZoneOffset.UTC),
            feeBasisPoints = null,
            feeFixedAmount = null,
            feeRoundingMode = null,
            feeCurrencyPrecision = null,
            feeCalculationAmount = null,
            eligibilityBasis = "CURRENT_EFFECTIVE_RECONCILIATION_MATCH",
            confirmationReason = null,
            confirmationEvidence = null,
            adjustmentSourceIdentity = null,
            adjustmentEvidence = null,
        )
    }

    private fun blocked(
        blockers: MutableList<String>,
        item: ReconciliationItem,
        reason: String,
    ): SettlementCandidateFact? {
        blockers += "${item.differenceIdentity}: $reason"
        return null
    }

    private fun within(value: Instant, start: Instant, end: Instant) = !value.isBefore(start) && value.isBefore(end)
}
