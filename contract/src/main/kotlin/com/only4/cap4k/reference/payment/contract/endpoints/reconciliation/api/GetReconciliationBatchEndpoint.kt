package com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * GET /api/reconciliation-batches/{batchId}
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetReconciliationBatchEndpoint",
    packageName = "reconciliation.api",
    description = "GET /api/reconciliation-batches/{batchId}",
    aggregates = [],
    operationName = "reconciliation.batch.get",
    family = "endpoint"
)
object GetReconciliationBatchEndpoint {
    const val OPERATION_NAME: String = "reconciliation.batch.get"

    data class Request(
        val batchId: String
    ) : EndpointRequest<Response>

    data class Response(
        val batchId: String,
        val channelId: String,
        val currency: String,
        val reconciliationDate: LocalDate,
        val businessTimezone: String,
        val status: String,
        val currentEffectiveRunId: String?,
        val statementWaitDeadlineAt: Instant,
        val matchedCount: Int,
        val differenceCount: Int,
        val unresolvedDifferenceCount: Int,
        val settlementBlocked: Boolean,
        val blockingReason: String?,
        val completedAt: Instant?,
        val runs: List<ReconciliationRunSummary>
    ) {
        data class ReconciliationRunSummary(
            val runId: String,
            val statementIdentity: String,
            val statementRevision: String,
            val statementCompleteness: String,
            val status: String,
            val fetchedAt: Instant,
            val startedAt: Instant,
            val completedAt: Instant?,
            val channelRecordCount: Int,
            val platformFactCount: Int,
            val matchedCount: Int,
            val differenceCount: Int,
            val unresolvedDifferenceCount: Int,
            val failureSummary: String?,
            val items: List<ReconciliationItemSummary>
        )
        data class ReconciliationItemSummary(
            val itemId: String,
            val differenceIdentity: String,
            val transactionKind: String,
            val differenceType: String,
            val channelRecordIdentity: String?,
            val channelTransactionIdentity: String?,
            val channelAmount: BigDecimal?,
            val channelCurrency: String?,
            val channelRawStatus: String?,
            val channelOccurredAt: Instant?,
            val channelReceivedAt: Instant?,
            val platformFactIdentity: String?,
            val paymentId: String?,
            val paymentAttemptId: String?,
            val refundId: String?,
            val refundAttemptId: String?,
            val platformTransactionIdentity: String?,
            val platformAmount: BigDecimal?,
            val platformCurrency: String?,
            val platformRawStatus: String?,
            val platformOccurredAt: Instant?,
            val platformRecordedAt: Instant?,
            val paymentReviewIdentitySnapshot: String?,
            val paymentReviewSummary: String?,
            val matchingBasis: String,
            val auxiliaryMatchApproved: Boolean,
            val resolved: Boolean,
            val settlementBlocked: Boolean,
            val dispositions: List<ReconciliationDispositionSummary>,
            val confirmationFacts: List<ReconciliationConfirmationFactSummary>
        )
        data class ReconciliationDispositionSummary(
            val dispositionId: String,
            val operatorIdentity: String,
            val operatorRole: String,
            val authorization: String,
            val status: String,
            val conclusion: String?,
            val settlementImpact: String,
            val evidence: String,
            val followUp: String?,
            val disposedAt: Instant
        )
        data class ReconciliationConfirmationFactSummary(
            val confirmationFactId: String,
            val sourceDifferenceIdentity: String,
            val operatorIdentity: String,
            val confirmationReason: String,
            val evidence: String,
            val transactionKind: String,
            val amount: BigDecimal,
            val currency: String,
            val externalTransactionIdentity: String,
            val paymentId: String?,
            val refundId: String?,
            val confirmedAt: Instant
        )
    }

}
