
package com.only4.cap4k.reference.payment.application.queries.merchant_settlement.read

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.Query
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "query",
    name = "GetMerchantSettlement",
    packageName = "merchant_settlement.read",
    description = "Read the full merchant settlement scope, lines, fee evidence, execution attempts, receipts, conflicts, and replacement trail",
    aggregates = ["MerchantSettlement"],
    family = "query"
)
object GetMerchantSettlementQry {

    data class Request(
        val settlementId: String
    ) : Query<Response>

    data class Response(
        val settlementId: String,
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val periodType: String,
        val periodStart: Instant,
        val periodEnd: Instant,
        val businessTimezone: String,
        val scopeIdentity: String,
        val effectiveScopeIdentity: String?,
        val status: String,
        val eligibleCount: Int,
        val excludedCount: Int,
        val blockerSummary: String?,
        val paymentGrossAmount: BigDecimal,
        val refundGrossAmount: BigDecimal,
        val feeTotalAmount: BigDecimal,
        val adjustmentTotalAmount: BigDecimal,
        val netAmount: BigDecimal,
        val compositionFrozen: Boolean,
        val executionGroupIdentity: String?,
        val predecessorSettlementId: String?,
        val replacementSettlementId: String?,
        val confirmedBy: String?,
        val confirmedAt: Instant?,
        val voidedBy: String?,
        val voidReason: String?,
        val voidedAt: Instant?,
        val settledFactFormed: Boolean,
        val externalSettlementIdentity: String?,
        val completedAt: Instant?,
        val lastRejectionSummary: String?,
        val lastConflictSummary: String?,
        val lastReviewSummary: String?,
        val lines: List<SettlementLineSummary>,
        val attempts: List<SettlementExecutionAttemptSummary>
    ) {
        data class SettlementLineSummary(
            val lineId: String,
            val lineIdentity: String,
            val sourceKind: String,
            val transactionKind: String,
            val sourceFactIdentity: String,
            val feeFactIdentity: String?,
            val paymentId: String?,
            val paymentAttemptId: String?,
            val refundId: String?,
            val refundAttemptId: String?,
            val reconciliationBatchId: String?,
            val reconciliationRunId: String?,
            val reconciliationItemId: String?,
            val reconciliationConfirmationFactId: String?,
            val externalTransactionIdentity: String,
            val grossAmount: BigDecimal,
            val feeAmount: BigDecimal,
            val signedNetAmount: BigDecimal,
            val currency: String,
            val occurredAt: Instant,
            val recordedAt: Instant,
            val feeBasisPoints: Int?,
            val feeFixedAmount: BigDecimal?,
            val feeRoundingMode: String?,
            val feeCurrencyPrecision: Int?,
            val feeCalculationAmount: BigDecimal?,
            val eligibilityBasis: String,
            val confirmationReason: String?,
            val confirmationEvidence: String?,
            val adjustmentSourceIdentity: String?,
            val adjustmentEvidence: String?
        )
        data class SettlementExecutionAttemptSummary(
            val attemptId: String,
            val attemptSequence: Int,
            val executionGroupIdentity: String,
            val requestIdentity: String,
            val channelId: String,
            val status: String,
            val initiatedAt: Instant,
            val acceptedAt: Instant?,
            val reviewAfterMinutesSnapshot: Int,
            val reviewAfterAt: Instant,
            val amount: BigDecimal,
            val currency: String,
            val externalSettlementIdentity: String?,
            val finalResult: String?,
            val resultOccurredAt: Instant?,
            val notificationReceiveCount: Int,
            val rejectionSummary: String?,
            val conflictSummary: String?,
            val receipts: List<SettlementResultReceiptSummary>
        )
        data class SettlementResultReceiptSummary(
            val receiptId: String,
            val notificationIdentity: String,
            val payloadFingerprint: String,
            val channelId: String,
            val executionGroupIdentity: String,
            val requestIdentity: String,
            val externalSettlementIdentity: String,
            val amount: BigDecimal,
            val currency: String,
            val result: String,
            val resultCode: String?,
            val occurredAt: Instant,
            val firstReceivedAt: Instant,
            val lastReceivedAt: Instant,
            val receiveCount: Int,
            val verified: Boolean,
            val accepted: Boolean,
            val decision: String,
            val verdictSummary: String?,
            val rejectionSummary: String?,
            val conflictSummary: String?
        )
    }

}
