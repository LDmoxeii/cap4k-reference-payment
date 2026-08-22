package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.prepare

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.candidate.LoadMerchantSettlementCandidates
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
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
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
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val channelId = command.channelId.trim()
            val currency = command.currency.trim().uppercase()
            require(merchantId.isNotBlank()) { "merchantId must not be blank" }
            require(channelId.isNotBlank()) { "channelId must not be blank" }
            require(currency.isNotBlank()) { "currency must not be blank" }
            require(command.requestedBy.isNotBlank()) { "requestedBy must not be blank" }

            val zone = ZoneId.of(BUSINESS_TIMEZONE)
            val periodStartInstant = command.settlementDate.atStartOfDay(zone).toInstant()
            val periodEndInstant = command.settlementDate.plusDays(1).atStartOfDay(zone).toInstant()
            val scopeIdentity = scopeIdentity(merchantId, channelId, currency, command.settlementDate)
            val existing = Mediator.repositories.findOne(
                SMerchantSettlement.predicate { schema -> schema.effectiveScopeIdentity eq scopeIdentity }
            )
            if (existing?.effectiveScopeIdentity == scopeIdentity) {
                return Response(existing.toOutcome(created = false, replay = true))
            }

            val candidates = Mediator.capabilities.call(
                LoadMerchantSettlementCandidates.Request(
                    merchantId = merchantId,
                    channelId = channelId,
                    currency = currency,
                    periodStart = periodStartInstant,
                    periodEnd = periodEndInstant,
                    businessTimezone = BUSINESS_TIMEZONE,
                )
            )
            val facts = candidates.eligibleFacts.sortedWith(compareBy({ it.occurredAt }, { it.sourceFactIdentity }))
            if (facts.isEmpty()) {
                return Response(
                    SettlementPreparationOutcome(
                        settlementId = null,
                        status = null,
                        created = false,
                        idempotentReplay = false,
                        noOp = true,
                        eligibleCount = 0,
                        excludedCount = candidates.excludedCount,
                        blockerSummary = candidates.blockerSummaries.distinct().joinToString("; ").ifBlank { null },
                        paymentGrossAmount = BigDecimal.ZERO,
                        refundGrossAmount = BigDecimal.ZERO,
                        feeTotalAmount = BigDecimal.ZERO,
                        adjustmentTotalAmount = BigDecimal.ZERO,
                        netAmount = BigDecimal.ZERO,
                    )
                )
            }
            facts.forEach { fact -> validateFact(fact, merchantId, channelId, currency, periodStartInstant, periodEndInstant) }
            val deferredActivation = command.predecessorSettlementId != null
            val lines = facts.map { fact -> toLineCreation(fact, active = !deferredActivation) }
            val paymentGross = facts.filter { it.transactionKind == ReconciliationTransactionKind.PAYMENT }
                .fold(BigDecimal.ZERO) { total, fact -> total + fact.grossAmount }
            val refundGross = facts.filter { it.transactionKind == ReconciliationTransactionKind.REFUND }
                .fold(BigDecimal.ZERO) { total, fact -> total + fact.grossAmount }
            val feeTotal = facts.fold(BigDecimal.ZERO) { total, fact -> total + fact.feeAmount }
            val adjustmentTotal = facts.filter { it.sourceKind == SettlementLineSourceKind.ADJUSTMENT }
                .fold(BigDecimal.ZERO) { total, fact -> total + fact.signedNetAmount }
            val netAmount = facts.fold(BigDecimal.ZERO) { total, fact -> total + fact.signedNetAmount }
            val initialStatus = if (netAmount.signum() < 0) {
                MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED
            } else {
                MerchantSettlementStatus.PREPARED
            }
            val settlement = Mediator.factories.create<MerchantSettlementFactory.Payload, MerchantSettlement>(
                MerchantSettlementFactory.Payload(
                    merchantId = merchantId,
                    channelId = channelId,
                    currency = currency,
                    periodStart = LocalDateTime.ofInstant(periodStartInstant, ZoneOffset.UTC),
                    periodEnd = LocalDateTime.ofInstant(periodEndInstant, ZoneOffset.UTC),
                    businessTimezone = BUSINESS_TIMEZONE,
                    scopeIdentity = scopeIdentity,
                    effectiveScopeIdentity = if (deferredActivation) null else scopeIdentity,
                    status = initialStatus,
                    eligibleCount = facts.size,
                    excludedCount = candidates.excludedCount,
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
            if (deferredActivation) {
                settlement.requestActivation()
            }
            return Response(settlement.toOutcome(created = true, replay = false))
        }

        private fun validateFact(
            fact: SettlementCandidateFact,
            merchantId: String,
            channelId: String,
            currency: String,
            periodStart: Instant,
            periodEnd: Instant,
        ) {
            require(fact.merchantId == merchantId) { "candidate ${fact.sourceFactIdentity} merchant attribution mismatch" }
            require(fact.channelId == channelId) { "candidate ${fact.sourceFactIdentity} channel attribution mismatch" }
            require(fact.currency == currency) { "candidate ${fact.sourceFactIdentity} currency mismatch" }
            require(!fact.occurredAt.isBefore(periodStart) && fact.occurredAt.isBefore(periodEnd)) {
                "candidate ${fact.sourceFactIdentity} is outside the settlement period"
            }
            require(fact.sourceFactIdentity.isNotBlank()) { "candidate sourceFactIdentity must not be blank" }
            if (fact.transactionKind == ReconciliationTransactionKind.PAYMENT) {
                require(fact.feeFactIdentity?.isNotBlank() == true) { "payment candidate is missing fee fact identity" }
                require(fact.feeBasisPoints != null && fact.feeFixedAmount != null && fact.feeRoundingMode != null &&
                    fact.feeCurrencyPrecision != null && fact.feeCalculationAmount != null) {
                    "payment candidate ${fact.sourceFactIdentity} has an incomplete fee snapshot"
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
            effectiveConsumptionIdentity = if (active) {
                stableIdentity("ACTIVE", fact.sourceKind.name, fact.sourceFactIdentity)
            } else null,
            feeFactIdentity = fact.feeFactIdentity,
            paymentId = fact.paymentId?.let(PaymentId::parse),
            paymentAttemptId = fact.paymentAttemptId,
            refundId = fact.refundId?.let(RefundId::parse),
            refundAttemptId = fact.refundAttemptId,
            reconciliationBatchId = ReconciliationBatchId.parse(fact.reconciliationBatchId),
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
            settlementId = id.toString(),
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

        private fun scopeIdentity(merchantId: String, channelId: String, currency: String, date: LocalDate) =
            "DAILY|$merchantId|$channelId|$currency|$date"

        private fun stableIdentity(vararg parts: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(parts.joinToString("|").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return digest.take(64)
        }
    }

    data class Request(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val settlementDate: LocalDate,
        val requestedBy: String,
        val requestedAt: Instant,
        val predecessorSettlementId: String?,
    ) : Command<Response>

    data class Response(val outcome: SettlementPreparationOutcome)

    private const val BUSINESS_TIMEZONE = "Asia/Shanghai"
}
