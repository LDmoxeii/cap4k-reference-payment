package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationConfirmationFact
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationConfirmationFactCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationDispositionCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationItem
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationItemCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationRun
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationRunCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.DispositionAuthorization
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationBatchStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDifferenceType
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionConclusion
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationRunStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.SettlementImpact
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "ReconciliationBatch",
    name = "ReconciliationBatchFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.factory",
    description = "",
    type = "factory",
    root = false
)
class ReconciliationBatchFactory : AggregateFactory<ReconciliationBatchFactory.Payload, ReconciliationBatch> {

    override fun create(entityPayload: Payload): ReconciliationBatch =
        ReconciliationBatch(
            channelId = entityPayload.channelId,
            currency = entityPayload.currency,
            reconciliationDate = entityPayload.reconciliationDate,
            businessTimezone = entityPayload.businessTimezone,
            status = entityPayload.status,
            currentEffectiveRunId = entityPayload.currentEffectiveRunId,
            statementWaitDeadlineAt = entityPayload.statementWaitDeadlineAt,
            matchedCount = entityPayload.matchedCount,
            differenceCount = entityPayload.differenceCount,
            unresolvedDifferenceCount = entityPayload.unresolvedDifferenceCount,
            settlementBlocked = entityPayload.settlementBlocked,
            blockingReason = entityPayload.blockingReason,
            completedAt = entityPayload.completedAt
        ).also { aggregate ->
            entityPayload.reconciliationRuns.forEach { childCreation ->
                aggregate.reconciliationRuns.add(createReconciliationRun(childCreation))
            }
        }

    private fun createReconciliationConfirmationFact(creation: ReconciliationConfirmationFactCreation): ReconciliationConfirmationFact =
        ReconciliationConfirmationFact(
            sourceDifferenceIdentity = creation.sourceDifferenceIdentity,
            merchantId = creation.merchantId,
            channelId = creation.channelId,
            operatorIdentity = creation.operatorIdentity,
            confirmationReason = creation.confirmationReason,
            evidence = creation.evidence,
            transactionKind = creation.transactionKind,
            amount = creation.amount,
            currency = creation.currency,
            externalTransactionIdentity = creation.externalTransactionIdentity,
            paymentId = creation.paymentId,
            refundId = creation.refundId,
            confirmedAt = creation.confirmedAt
        )

    private fun createReconciliationDisposition(creation: ReconciliationDispositionCreation): ReconciliationDisposition =
        ReconciliationDisposition(
            operatorIdentity = creation.operatorIdentity,
            operatorRole = creation.operatorRole,
            authorizationResult = creation.authorizationResult,
            status = creation.status,
            conclusion = creation.conclusion,
            settlementImpact = creation.settlementImpact,
            evidence = creation.evidence,
            followUp = creation.followUp,
            disposedAt = creation.disposedAt
        )

    private fun createReconciliationItem(creation: ReconciliationItemCreation): ReconciliationItem =
        ReconciliationItem(
            differenceIdentity = creation.differenceIdentity,
            transactionKind = creation.transactionKind,
            differenceType = creation.differenceType,
            channelRecordIdentity = creation.channelRecordIdentity,
            channelTransactionIdentity = creation.channelTransactionIdentity,
            channelAmount = creation.channelAmount,
            channelCurrency = creation.channelCurrency,
            channelRawStatus = creation.channelRawStatus,
            channelOccurredAt = creation.channelOccurredAt,
            channelReceivedAt = creation.channelReceivedAt,
            platformFactIdentity = creation.platformFactIdentity,
            paymentId = creation.paymentId,
            paymentAttemptId = creation.paymentAttemptId,
            refundId = creation.refundId,
            refundAttemptId = creation.refundAttemptId,
            platformTransactionIdentity = creation.platformTransactionIdentity,
            platformAmount = creation.platformAmount,
            platformCurrency = creation.platformCurrency,
            platformRawStatus = creation.platformRawStatus,
            platformOccurredAt = creation.platformOccurredAt,
            platformRecordedAt = creation.platformRecordedAt,
            paymentReviewIdentitySnapshot = creation.paymentReviewIdentitySnapshot,
            paymentReviewSummary = creation.paymentReviewSummary,
            matchingBasis = creation.matchingBasis,
            auxiliaryMatchApproved = creation.auxiliaryMatchApproved,
            resolved = creation.resolved,
            settlementBlocked = creation.settlementBlocked
        ).also { entity ->
            creation.reconciliationConfirmationFacts.forEach { childCreation ->
                entity.reconciliationConfirmationFacts.add(createReconciliationConfirmationFact(childCreation))
            }
            creation.reconciliationDispositions.forEach { childCreation ->
                entity.reconciliationDispositions.add(createReconciliationDisposition(childCreation))
            }
        }

    private fun createReconciliationRun(creation: ReconciliationRunCreation): ReconciliationRun =
        ReconciliationRun(
            statementIdentity = creation.statementIdentity,
            statementRevision = creation.statementRevision,
            statementCompleteness = creation.statementCompleteness,
            status = creation.status,
            fetchedAt = creation.fetchedAt,
            startedAt = creation.startedAt,
            completedAt = creation.completedAt,
            channelRecordCount = creation.channelRecordCount,
            platformFactCount = creation.platformFactCount,
            matchedCount = creation.matchedCount,
            differenceCount = creation.differenceCount,
            unresolvedDifferenceCount = creation.unresolvedDifferenceCount,
            failureSummary = creation.failureSummary
        ).also { entity ->
            creation.reconciliationItems.forEach { childCreation ->
                entity.reconciliationItems.add(createReconciliationItem(childCreation))
            }
        }

    data class Payload(
        val channelId: String,
        val currency: String,
        val reconciliationDate: LocalDate,
        val businessTimezone: String,
        val status: ReconciliationBatchStatus,
        val currentEffectiveRunId: String?,
        val statementWaitDeadlineAt: LocalDateTime,
        val matchedCount: Int = 0,
        val differenceCount: Int = 0,
        val unresolvedDifferenceCount: Int = 0,
        val settlementBlocked: Boolean = true,
        val blockingReason: String?,
        val completedAt: LocalDateTime?,
        val reconciliationRuns: List<ReconciliationRunCreation> = emptyList()
    ) : AggregatePayload<ReconciliationBatch>
}
