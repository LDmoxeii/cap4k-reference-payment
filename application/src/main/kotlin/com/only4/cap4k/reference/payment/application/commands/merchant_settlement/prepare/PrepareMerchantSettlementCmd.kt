package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.prepare

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.candidate.LoadMerchantSettlementCandidates
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.application.manual_review.openNegativeSettlementReview
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementConflictException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementRejectedException
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementLineCreation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementLineSourceKind
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.requestActivation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.factory.MerchantSettlementFactory
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementCandidateFact
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementPreparationOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "PrepareMerchantSettlement",
    packageName = "merchant_settlement.prepare",
    description = "Idempotently prepare one daily merchant settlement from current effective reconciliation facts",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object PrepareMerchantSettlementCmd {

    @Service
    class Handler(
        private val manualReviewSupport: ManualReviewSupport,
        private val operationSupport: OperationSupport,
    ) : CommandHandler<Request, Response> {
        /**
         * 以 merchant/currency/settlementPeriod 形成稳定 scope，先复用当前有效结算单，再读取所有渠道的
         * current-effective-run 候选。渠道只保留在 candidate/line 的来源证据中，不能拆分 scope。
         * 候选必须逐条核对归属、币种、`[periodStart, periodEnd)` 和 Payment fee snapshot；通过后一次性冻结
         * SettlementLine 与 root 汇总。replacement 在 predecessor 释放前延迟激活，避免短暂双重消费。
         */
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val currency = command.currency.trim().uppercase()
            require(merchantId.isNotBlank()) { "商户身份不能为空" }
            require(currency.isNotBlank()) { "币种不能为空" }
            require(command.requestedBy.isNotBlank()) { "请求操作员不能为空" }
            val idempotencyKey = command.idempotencyKey.trim()
            require(idempotencyKey.isNotBlank()) { "幂等键不能为空" }

            val timezone = command.businessTimezone.trim().ifBlank { BUSINESS_TIMEZONE }
            ZoneId.of(timezone)
            val periodStartInstant = command.periodStart
            val periodEndInstant = command.periodEnd
            require(periodEndInstant > periodStartInstant) { "结算周期结束时间必须晚于开始时间" }
            val requestHash = operationSupport.canonicalHash(
                merchantId, currency, periodStartInstant.toString(), periodEndInstant.toString(),
                timezone, command.requestedBy.trim(), command.predecessorSettlementId?.toString(),
            )
            operationSupport.replayOrNull(merchantId, COMMAND_TYPE, idempotencyKey, requestHash)?.let { operation ->
                val original = Mediator.repositories.findOne(
                    SMerchantSettlement.predicateById(MerchantSettlementId.parse(operation.resourceId)),
                ) ?: error("operation ${operation.id} refers to missing settlement ${operation.resourceId}")
                return Response(
                    original.toOutcome(created = true, replay = true),
                    operationSupport.receipt(operation, replay = true),
                )
            }
            val scopeIdentity = scopeIdentity(merchantId, currency, periodStartInstant, periodEndInstant, timezone)
            val existing = Mediator.repositories.findOne(
                SMerchantSettlement.predicate { schema -> schema.effectiveScopeIdentity eq scopeIdentity }
            )
            if (existing?.effectiveScopeIdentity == scopeIdentity) {
                throw MerchantSettlementConflictException(
                    "SETTLEMENT_SCOPE_CONFLICT",
                    "结算范围 $scopeIdentity 已有有效结算单 ${existing.id}",
                )
            }

            val candidates = Mediator.capabilities.call(
                LoadMerchantSettlementCandidates.Request(
                    merchantId = merchantId,
                    currency = currency,
                    periodStart = periodStartInstant,
                    periodEnd = periodEndInstant,
                    businessTimezone = timezone,
                    predecessorSettlementId = command.predecessorSettlementId?.toString(),
                )
            )
            val facts = candidates.eligibleFacts.sortedWith(compareBy({ it.occurredAt }, { it.sourceFactIdentity }))
            val excludedFacts = candidates.excludedFacts.sortedWith(compareBy({ it.occurredAt }, { it.sourceFactIdentity }))
            if (facts.isEmpty() && excludedFacts.isEmpty()) {
                throw MerchantSettlementRejectedException(
                    "MERCHANT_SETTLEMENT_NO_CANDIDATES",
                    "当前结算范围没有可冻结的候选事实",
                )
            }
            facts.forEach { fact -> validateFact(fact, merchantId, currency, periodStartInstant, periodEndInstant) }
            val deferredActivation = command.predecessorSettlementId != null
            val lines = facts.map { fact -> toLineCreation(fact, active = !deferredActivation) } +
                excludedFacts.map { fact -> toLineCreation(fact, active = false) }
            val paymentGross = facts.filter { it.sourceKind == SettlementLineSourceKind.PAYMENT }
                .fold(BigDecimal.ZERO) { total, fact -> total + fact.grossAmount }
            val refundGross = facts.filter { it.sourceKind == SettlementLineSourceKind.REFUND }
                .fold(BigDecimal.ZERO) { total, fact -> total + fact.grossAmount }
            val feeTotal = facts.fold(BigDecimal.ZERO) { total, fact -> total + fact.feeAmount }
            val adjustmentTotal = facts.filter { it.sourceKind == SettlementLineSourceKind.ADJUSTMENT }
                .fold(BigDecimal.ZERO) { total, fact -> total + fact.signedNetAmount }
            val netAmount = facts.fold(BigDecimal.ZERO) { total, fact -> total + fact.signedNetAmount }
            val initialStatus = when {
                facts.isEmpty() -> MerchantSettlementStatus.REVIEW_REQUIRED
                netAmount.signum() < 0 -> MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED
                else -> MerchantSettlementStatus.PREPARED
            }
            val settlement = Mediator.factories.create<MerchantSettlementFactory.Payload, MerchantSettlement>(
                MerchantSettlementFactory.Payload(
                    merchantId = merchantId,
                    executionChannelId = null,
                    currency = currency,
                    periodType = "PERIOD",
                    periodStart = LocalDateTime.ofInstant(periodStartInstant, ZoneOffset.UTC),
                    periodEnd = LocalDateTime.ofInstant(periodEndInstant, ZoneOffset.UTC),
                    businessTimezone = timezone,
                    scopeIdentity = scopeIdentity,
                    effectiveScopeIdentity = if (deferredActivation) null else scopeIdentity,
                    status = initialStatus,
                    eligibleCount = facts.size,
                    excludedCount = excludedFacts.size,
                    blockerSummary = candidates.blockerSummaries.distinct().joinToString("; ").ifBlank { null },
                    paymentGrossAmount = paymentGross,
                    refundGrossAmount = refundGross,
                    feeTotalAmount = feeTotal,
                    adjustmentTotalAmount = adjustmentTotal,
                    netAmount = netAmount,
                    compositionFrozen = false,
                    executionGroupIdentity = null,
                    predecessorSettlementId = command.predecessorSettlementId,
                    replacementSettlementId = null,
                    confirmedBy = null,
                    confirmedAt = null,
                    voidedBy = null,
                    voidReason = null,
                    voidedAt = null,
                    settledFactFormed = false,
                    externalSettlementIdentity = null,
                    completedAt = null,
                    lastRejectionSummary = null,
                    lastConflictSummary = null,
                    lastReviewSummary = null,
                    settlementLines = lines,
                )
            )
            if (deferredActivation && facts.isNotEmpty()) {
                settlement.requestActivation()
            }
            if (settlement.status == MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED) {
                manualReviewSupport.openNegativeSettlementReview(settlement)
            }
            val receipt = operationSupport.accept(
                merchantId, COMMAND_TYPE, idempotencyKey, requestHash,
                "MerchantSettlement", settlement.id.toString(), "/api/merchant-settlements/${settlement.id}",
            )
            return Response(settlement.toOutcome(created = true, replay = false), receipt)
        }

        private fun validateFact(
            fact: SettlementCandidateFact,
            merchantId: String,
            currency: String,
            periodStart: Instant,
            periodEnd: Instant,
        ) {
            require(fact.merchantId == merchantId) { "候选事实 ${fact.sourceFactIdentity} 的商户归属不一致" }
            require(fact.currency == currency) { "候选事实 ${fact.sourceFactIdentity} 的币种不一致" }
            require(!fact.occurredAt.isBefore(periodStart) && fact.occurredAt.isBefore(periodEnd)) {
                "候选事实 ${fact.sourceFactIdentity} 不在结算周期内"
            }
            require(fact.sourceFactIdentity.isNotBlank()) { "候选事实身份不能为空" }
            if (fact.sourceKind == SettlementLineSourceKind.PAYMENT) {
                require(fact.feeFactIdentity?.isNotBlank() == true) { "支付候选缺少手续费事实身份" }
                require(fact.feeBasisPoints != null && fact.feeFixedAmount != null && fact.feeRoundingMode != null &&
                    fact.feeCurrencyPrecision != null && fact.feeCalculationAmount != null) {
                    "支付候选 ${fact.sourceFactIdentity} 缺少完整手续费快照"
                }
            }
        }

        private fun toLineCreation(
            fact: SettlementCandidateFact,
            active: Boolean,
        ): SettlementLineCreation = SettlementLineCreation(
            lineIdentity = stableIdentity("LINE", fact.sourceKind.name, fact.sourceFactIdentity),
            sourceKind = fact.sourceKind,
            transactionKind = fact.transactionKind,
            sourceFactIdentity = fact.sourceFactIdentity,
            decision = fact.decision,
            reasonCode = fact.reasonCode,
            effectiveConsumptionIdentity = if (active && fact.decision == "INCLUDED") {
                stableIdentity("ACTIVE", fact.sourceKind.name, fact.sourceFactIdentity)
            } else null,
            feeFactIdentity = fact.feeFactIdentity,
            paymentId = fact.paymentId,
            paymentAttemptId = fact.paymentAttemptId,
            refundId = fact.refundId,
            refundAttemptId = fact.refundAttemptId,
            reconciliationBatchId = fact.reconciliationBatchId,
            reconciliationRunId = fact.reconciliationRunId,
            reconciliationItemId = fact.reconciliationItemId,
            reconciliationConfirmationFactId = fact.reconciliationConfirmationFactId,
            externalTransactionIdentity = fact.externalTransactionIdentity,
            grossAmount = fact.grossAmount,
            feeAmount = fact.feeAmount,
            signedNetAmount = fact.signedNetAmount,
            currency = fact.currency,
            occurredAt = LocalDateTime.ofInstant(fact.occurredAt, ZoneOffset.UTC),
            recordedAt = LocalDateTime.ofInstant(fact.recordedAt, ZoneOffset.UTC),
            feeBasisPoints = fact.feeBasisPoints,
            feeFixedAmount = fact.feeFixedAmount,
            feeRoundingMode = fact.feeRoundingMode,
            feeCurrencyPrecision = fact.feeCurrencyPrecision,
            feeCalculationAmount = fact.feeCalculationAmount,
            eligibilityBasis = fact.eligibilityBasis,
            confirmationReason = fact.confirmationReason,
            confirmationEvidence = fact.confirmationEvidence,
            adjustmentSourceIdentity = fact.adjustmentSourceIdentity,
            adjustmentEvidence = fact.adjustmentEvidence,
        )

        private fun MerchantSettlement.toOutcome(created: Boolean, replay: Boolean) = SettlementPreparationOutcome(
            merchantSettlementId = id,
            status = status,
            created = created,
            idempotentReplay = replay,
            noOp = false,
            eligibleCount = eligibleCount,
            excludedCount = excludedCount,
            blockerSummary = blockerSummary,
            paymentGrossAmount = paymentGrossAmount,
            refundGrossAmount = refundGrossAmount,
            feeTotalAmount = feeTotalAmount,
            adjustmentTotalAmount = adjustmentTotalAmount,
            netAmount = netAmount,
        )

        private fun scopeIdentity(
            merchantId: String,
            currency: String,
            periodStart: Instant,
            periodEnd: Instant,
            timezone: String,
        ) = "PERIOD|$merchantId|$currency|$periodStart|$periodEnd|$timezone"

        private fun stableIdentity(vararg parts: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(parts.joinToString("|").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return digest.take(64)
        }
    }

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 结算周期开始（包含）
         */
        val periodStart: Instant,
        /**
         * 结算周期结束（不包含）
         */
        val periodEnd: Instant,
        /**
         * 结算周期业务时区；默认 Asia/Shanghai
         */
        val businessTimezone: String = BUSINESS_TIMEZONE,
        /**
         * 请求操作人
         */
        val requestedBy: String,
        val idempotencyKey: String,
        /**
         * 请求时间
         */
        val requestedAt: Instant,
        /**
         * 前序结算标识
         */
        val predecessorSettlementId: MerchantSettlementId?,
    ) : Command<Response>

    data class Response(val outcome: SettlementPreparationOutcome, val receipt: OperationReceipt)

    private const val BUSINESS_TIMEZONE = "Asia/Shanghai"
    private const val COMMAND_TYPE = "PrepareMerchantSettlement"
}
