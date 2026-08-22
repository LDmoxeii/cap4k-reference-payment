package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementExecutionAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementExecutionAttemptCreation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementLine
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementLineCreation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementResultReceipt
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementResultReceiptCreation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementLineSourceKind
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "MerchantSettlement",
    name = "MerchantSettlementFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.factory",
    description = "",
    type = "factory",
    root = false
)
class MerchantSettlementFactory : AggregateFactory<MerchantSettlementFactory.Payload, MerchantSettlement> {

    override fun create(entityPayload: Payload): MerchantSettlement =
        MerchantSettlement(
            merchantId = entityPayload.merchantId,
            channelId = entityPayload.channelId,
            currency = entityPayload.currency,
            periodType = entityPayload.periodType,
            periodStart = entityPayload.periodStart,
            periodEnd = entityPayload.periodEnd,
            businessTimezone = entityPayload.businessTimezone,
            scopeIdentity = entityPayload.scopeIdentity,
            effectiveScopeIdentity = entityPayload.effectiveScopeIdentity,
            status = entityPayload.status,
            eligibleCount = entityPayload.eligibleCount,
            excludedCount = entityPayload.excludedCount,
            blockerSummary = entityPayload.blockerSummary,
            paymentGrossAmount = entityPayload.paymentGrossAmount,
            refundGrossAmount = entityPayload.refundGrossAmount,
            feeTotalAmount = entityPayload.feeTotalAmount,
            adjustmentTotalAmount = entityPayload.adjustmentTotalAmount,
            netAmount = entityPayload.netAmount,
            compositionFrozen = entityPayload.compositionFrozen,
            executionGroupIdentity = entityPayload.executionGroupIdentity,
            predecessorSettlementId = entityPayload.predecessorSettlementId,
            replacementSettlementId = entityPayload.replacementSettlementId,
            confirmedBy = entityPayload.confirmedBy,
            confirmedAt = entityPayload.confirmedAt,
            voidedBy = entityPayload.voidedBy,
            voidReason = entityPayload.voidReason,
            voidedAt = entityPayload.voidedAt,
            settledFactFormed = entityPayload.settledFactFormed,
            externalSettlementIdentity = entityPayload.externalSettlementIdentity,
            completedAt = entityPayload.completedAt,
            lastRejectionSummary = entityPayload.lastRejectionSummary,
            lastConflictSummary = entityPayload.lastConflictSummary,
            lastReviewSummary = entityPayload.lastReviewSummary
        ).also { aggregate ->
            entityPayload.settlementExecutionAttempts.forEach { childCreation ->
                aggregate.settlementExecutionAttempts.add(createSettlementExecutionAttempt(childCreation))
            }
            entityPayload.settlementLines.forEach { childCreation ->
                aggregate.settlementLines.add(createSettlementLine(childCreation))
            }
        }

    private fun createSettlementResultReceipt(creation: SettlementResultReceiptCreation): SettlementResultReceipt =
        SettlementResultReceipt(
            notificationIdentity = creation.notificationIdentity,
            payloadFingerprint = creation.payloadFingerprint,
            channelId = creation.channelId,
            executionGroupIdentity = creation.executionGroupIdentity,
            requestIdentity = creation.requestIdentity,
            externalSettlementIdentity = creation.externalSettlementIdentity,
            amount = creation.amount,
            currency = creation.currency,
            result = creation.result,
            resultCode = creation.resultCode,
            occurredAt = creation.occurredAt,
            firstReceivedAt = creation.firstReceivedAt,
            lastReceivedAt = creation.lastReceivedAt,
            receiveCount = creation.receiveCount,
            verified = creation.verified,
            accepted = creation.accepted,
            decision = creation.decision,
            verdictSummary = creation.verdictSummary,
            rejectionSummary = creation.rejectionSummary,
            conflictSummary = creation.conflictSummary
        )

    private fun createSettlementExecutionAttempt(creation: SettlementExecutionAttemptCreation): SettlementExecutionAttempt =
        SettlementExecutionAttempt(
            attemptSequence = creation.attemptSequence,
            executionGroupIdentity = creation.executionGroupIdentity,
            requestIdentity = creation.requestIdentity,
            channelId = creation.channelId,
            status = creation.status,
            initiatedAt = creation.initiatedAt,
            acceptedAt = creation.acceptedAt,
            reviewAfterMinutesSnapshot = creation.reviewAfterMinutesSnapshot,
            reviewAfterAt = creation.reviewAfterAt,
            amount = creation.amount,
            currency = creation.currency,
            externalSettlementIdentity = creation.externalSettlementIdentity,
            finalResult = creation.finalResult,
            resultOccurredAt = creation.resultOccurredAt,
            notificationReceiveCount = creation.notificationReceiveCount,
            notificationFirstReceivedAt = creation.notificationFirstReceivedAt,
            notificationLastReceivedAt = creation.notificationLastReceivedAt,
            verifiedNotificationCount = creation.verifiedNotificationCount,
            rejectedNotificationCount = creation.rejectedNotificationCount,
            conflictingNotificationCount = creation.conflictingNotificationCount,
            verdictSummary = creation.verdictSummary,
            rejectionSummary = creation.rejectionSummary,
            conflictSummary = creation.conflictSummary
        ).also { entity ->
            creation.settlementResultReceipts.forEach { childCreation ->
                entity.settlementResultReceipts.add(createSettlementResultReceipt(childCreation))
            }
        }

    private fun createSettlementLine(creation: SettlementLineCreation): SettlementLine =
        SettlementLine(
            lineIdentity = creation.lineIdentity,
            sourceKind = creation.sourceKind,
            transactionKind = creation.transactionKind,
            sourceFactIdentity = creation.sourceFactIdentity,
            effectiveConsumptionIdentity = creation.effectiveConsumptionIdentity,
            feeFactIdentity = creation.feeFactIdentity,
            paymentId = creation.paymentId,
            paymentAttemptId = creation.paymentAttemptId,
            refundId = creation.refundId,
            refundAttemptId = creation.refundAttemptId,
            reconciliationBatchId = creation.reconciliationBatchId,
            reconciliationRunId = creation.reconciliationRunId,
            reconciliationItemId = creation.reconciliationItemId,
            reconciliationConfirmationFactId = creation.reconciliationConfirmationFactId,
            externalTransactionIdentity = creation.externalTransactionIdentity,
            grossAmount = creation.grossAmount,
            feeAmount = creation.feeAmount,
            signedNetAmount = creation.signedNetAmount,
            currency = creation.currency,
            occurredAt = creation.occurredAt,
            recordedAt = creation.recordedAt,
            feeBasisPoints = creation.feeBasisPoints,
            feeFixedAmount = creation.feeFixedAmount,
            feeRoundingMode = creation.feeRoundingMode,
            feeCurrencyPrecision = creation.feeCurrencyPrecision,
            feeCalculationAmount = creation.feeCalculationAmount,
            eligibilityBasis = creation.eligibilityBasis,
            confirmationReason = creation.confirmationReason,
            confirmationEvidence = creation.confirmationEvidence,
            adjustmentSourceIdentity = creation.adjustmentSourceIdentity,
            adjustmentEvidence = creation.adjustmentEvidence
        )

    data class Payload(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val periodType: String = "DAILY",
        val periodStart: LocalDateTime,
        val periodEnd: LocalDateTime,
        val businessTimezone: String,
        val scopeIdentity: String,
        val effectiveScopeIdentity: String?,
        val status: MerchantSettlementStatus,
        val eligibleCount: Int = 0,
        val excludedCount: Int = 0,
        val blockerSummary: String?,
        val paymentGrossAmount: BigDecimal,
        val refundGrossAmount: BigDecimal,
        val feeTotalAmount: BigDecimal,
        val adjustmentTotalAmount: BigDecimal,
        val netAmount: BigDecimal,
        val compositionFrozen: Boolean = false,
        val executionGroupIdentity: String?,
        val predecessorSettlementId: String?,
        val replacementSettlementId: String?,
        val confirmedBy: String?,
        val confirmedAt: LocalDateTime?,
        val voidedBy: String?,
        val voidReason: String?,
        val voidedAt: LocalDateTime?,
        val settledFactFormed: Boolean = false,
        val externalSettlementIdentity: String?,
        val completedAt: LocalDateTime?,
        val lastRejectionSummary: String?,
        val lastConflictSummary: String?,
        val lastReviewSummary: String?,
        val settlementExecutionAttempts: List<SettlementExecutionAttemptCreation> = emptyList(),
        val settlementLines: List<SettlementLineCreation> = emptyList()
    ) : AggregatePayload<MerchantSettlement>
}
