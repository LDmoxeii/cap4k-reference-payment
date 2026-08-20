package com.only4.cap4k.reference.payment.adapter.application.queries.reconciliation.read

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.application.queries.reconciliation.read.GetReconciliationBatchQry
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "GetReconciliationBatch",
    packageName = "reconciliation.read",
    description = "Read a reconciliation batch with immutable run, item, disposition, and confirmation evidence",
    aggregates = ["ReconciliationBatch"],
    family = "query-handler"
)
class GetReconciliationBatchQryHandler : QueryHandler<GetReconciliationBatchQry.Request, GetReconciliationBatchQry.Response> {

    override fun handle(query: GetReconciliationBatchQry.Request): GetReconciliationBatchQry.Response {
        val batch = Mediator.repositories.findOne(
            SReconciliationBatch.predicateById(ReconciliationBatchId.parse(query.batchId))
        ) ?: throw ReconciliationBatchNotFoundException(query.batchId)
        return GetReconciliationBatchQry.Response(
            batchId = batch.id.toString(),
            channelId = batch.channelId,
            currency = batch.currency,
            reconciliationDate = batch.reconciliationDate,
            businessTimezone = batch.businessTimezone,
            status = batch.status.name,
            currentEffectiveRunId = batch.currentEffectiveRunId,
            statementWaitDeadlineAt = batch.statementWaitDeadlineAt.toInstant(ZoneOffset.UTC),
            matchedCount = batch.matchedCount,
            differenceCount = batch.differenceCount,
            unresolvedDifferenceCount = batch.unresolvedDifferenceCount,
            settlementBlocked = batch.settlementBlocked,
            blockingReason = batch.blockingReason,
            completedAt = batch.completedAt?.toInstant(ZoneOffset.UTC),
            runs = batch.reconciliationRuns.map { run ->
                GetReconciliationBatchQry.Response.ReconciliationRunSummary(
                    runId = run.id.toString(),
                    statementIdentity = run.statementIdentity,
                    statementRevision = run.statementRevision,
                    statementCompleteness = run.statementCompleteness.name,
                    status = run.status.name,
                    fetchedAt = run.fetchedAt.toInstant(ZoneOffset.UTC),
                    startedAt = run.startedAt.toInstant(ZoneOffset.UTC),
                    completedAt = run.completedAt?.toInstant(ZoneOffset.UTC),
                    channelRecordCount = run.channelRecordCount,
                    platformFactCount = run.platformFactCount,
                    matchedCount = run.matchedCount,
                    differenceCount = run.differenceCount,
                    unresolvedDifferenceCount = run.unresolvedDifferenceCount,
                    failureSummary = run.failureSummary,
                    items = run.reconciliationItems.map { item ->
                        GetReconciliationBatchQry.Response.ReconciliationItemSummary(
                            itemId = item.id.toString(),
                            differenceIdentity = item.differenceIdentity,
                            transactionKind = item.transactionKind.name,
                            differenceType = item.differenceType.name,
                            channelRecordIdentity = item.channelRecordIdentity,
                            channelTransactionIdentity = item.channelTransactionIdentity,
                            channelAmount = item.channelAmount,
                            channelCurrency = item.channelCurrency,
                            channelRawStatus = item.channelRawStatus,
                            channelOccurredAt = item.channelOccurredAt?.toInstant(ZoneOffset.UTC),
                            channelReceivedAt = item.channelReceivedAt?.toInstant(ZoneOffset.UTC),
                            platformFactIdentity = item.platformFactIdentity,
                            paymentId = item.paymentId,
                            paymentAttemptId = item.paymentAttemptId,
                            refundId = item.refundId,
                            refundAttemptId = item.refundAttemptId,
                            platformTransactionIdentity = item.platformTransactionIdentity,
                            platformAmount = item.platformAmount,
                            platformCurrency = item.platformCurrency,
                            platformRawStatus = item.platformRawStatus,
                            platformOccurredAt = item.platformOccurredAt?.toInstant(ZoneOffset.UTC),
                            platformRecordedAt = item.platformRecordedAt?.toInstant(ZoneOffset.UTC),
                            matchingBasis = item.matchingBasis,
                            auxiliaryMatchApproved = item.auxiliaryMatchApproved,
                            resolved = item.resolved,
                            settlementBlocked = item.settlementBlocked,
                            dispositions = item.reconciliationDispositions.map { disposition ->
                                GetReconciliationBatchQry.Response.ReconciliationDispositionSummary(
                                    dispositionId = disposition.id.toString(),
                                    operatorIdentity = disposition.operatorIdentity,
                                    operatorRole = disposition.operatorRole,
                                    authorization = disposition.authorizationResult.name,
                                    status = disposition.status.name,
                                    conclusion = disposition.conclusion?.name,
                                    settlementImpact = disposition.settlementImpact.name,
                                    evidence = disposition.evidence,
                                    followUp = disposition.followUp,
                                    disposedAt = disposition.disposedAt.toInstant(ZoneOffset.UTC),
                                )
                            },
                            confirmationFacts = item.reconciliationConfirmationFacts.map { confirmation ->
                                GetReconciliationBatchQry.Response.ReconciliationConfirmationFactSummary(
                                    confirmationFactId = confirmation.id.toString(),
                                    sourceDifferenceIdentity = confirmation.sourceDifferenceIdentity,
                                    operatorIdentity = confirmation.operatorIdentity,
                                    confirmationReason = confirmation.confirmationReason,
                                    evidence = confirmation.evidence,
                                    transactionKind = confirmation.transactionKind.name,
                                    amount = confirmation.amount,
                                    currency = confirmation.currency,
                                    externalTransactionIdentity = confirmation.externalTransactionIdentity,
                                    paymentId = confirmation.paymentId,
                                    refundId = confirmation.refundId,
                                    confirmedAt = confirmation.confirmedAt.toInstant(ZoneOffset.UTC),
                                )
                            },
                        )
                    },
                )
            },
        )
    }
}
