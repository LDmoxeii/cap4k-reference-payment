package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.queries.reconciliation.read.GetReconciliationBatchQry
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.GetReconciliationBatchEndpoint
import org.springframework.stereotype.Component

@Component
class GetReconciliationBatchEndpointHandler : EndpointHandler<GetReconciliationBatchEndpoint.Request, GetReconciliationBatchEndpoint.Response> {
    override fun handle(request: GetReconciliationBatchEndpoint.Request): GetReconciliationBatchEndpoint.Response {
        val response = Mediator.queries.ask(GetReconciliationBatchQry.Request(request.batchId))
        return GetReconciliationBatchEndpoint.Response(
            batchId = response.batchId,
            channelId = response.channelId,
            currency = response.currency,
            reconciliationDate = response.reconciliationDate,
            businessTimezone = response.businessTimezone,
            status = response.status,
            currentEffectiveRunId = response.currentEffectiveRunId,
            statementWaitDeadlineAt = response.statementWaitDeadlineAt,
            matchedCount = response.matchedCount,
            differenceCount = response.differenceCount,
            unresolvedDifferenceCount = response.unresolvedDifferenceCount,
            settlementBlocked = response.settlementBlocked,
            blockingReason = response.blockingReason,
            completedAt = response.completedAt,
            runs = response.runs.map { run ->
                GetReconciliationBatchEndpoint.Response.ReconciliationRunSummary(
                    runId = run.runId,
                    statementIdentity = run.statementIdentity,
                    statementRevision = run.statementRevision,
                    statementCompleteness = run.statementCompleteness,
                    status = run.status,
                    fetchedAt = run.fetchedAt,
                    startedAt = run.startedAt,
                    completedAt = run.completedAt,
                    channelRecordCount = run.channelRecordCount,
                    platformFactCount = run.platformFactCount,
                    matchedCount = run.matchedCount,
                    differenceCount = run.differenceCount,
                    unresolvedDifferenceCount = run.unresolvedDifferenceCount,
                    failureSummary = run.failureSummary,
                    items = run.items.map { item ->
                        GetReconciliationBatchEndpoint.Response.ReconciliationItemSummary(
                            itemId = item.itemId,
                            differenceIdentity = item.differenceIdentity,
                            transactionKind = item.transactionKind,
                            differenceType = item.differenceType,
                            channelRecordIdentity = item.channelRecordIdentity,
                            channelTransactionIdentity = item.channelTransactionIdentity,
                            channelAmount = item.channelAmount,
                            channelCurrency = item.channelCurrency,
                            channelRawStatus = item.channelRawStatus,
                            channelOccurredAt = item.channelOccurredAt,
                            channelReceivedAt = item.channelReceivedAt,
                            platformFactIdentity = item.platformFactIdentity,
                            paymentId = item.paymentId,
                            paymentAttemptId = item.paymentAttemptId,
                            refundId = item.refundId,
                            refundAttemptId = item.refundAttemptId,
                            platformTransactionIdentity = item.platformTransactionIdentity,
                            platformAmount = item.platformAmount,
                            platformCurrency = item.platformCurrency,
                            platformRawStatus = item.platformRawStatus,
                            platformOccurredAt = item.platformOccurredAt,
                            platformRecordedAt = item.platformRecordedAt,
                            paymentReviewIdentitySnapshot = item.paymentReviewIdentitySnapshot,
                            paymentReviewSummary = item.paymentReviewSummary,
                            matchingBasis = item.matchingBasis,
                            auxiliaryMatchApproved = item.auxiliaryMatchApproved,
                            resolved = item.resolved,
                            settlementBlocked = item.settlementBlocked,
                            dispositions = item.dispositions.map { disposition ->
                                GetReconciliationBatchEndpoint.Response.ReconciliationDispositionSummary(
                                    dispositionId = disposition.dispositionId,
                                    operatorIdentity = disposition.operatorIdentity,
                                    operatorRole = disposition.operatorRole,
                                    authorization = disposition.authorization,
                                    status = disposition.status,
                                    conclusion = disposition.conclusion,
                                    settlementImpact = disposition.settlementImpact,
                                    evidence = disposition.evidence,
                                    followUp = disposition.followUp,
                                    disposedAt = disposition.disposedAt,
                                )
                            },
                            confirmationFacts = item.confirmationFacts.map { confirmation ->
                                GetReconciliationBatchEndpoint.Response.ReconciliationConfirmationFactSummary(
                                    confirmationFactId = confirmation.confirmationFactId,
                                    sourceDifferenceIdentity = confirmation.sourceDifferenceIdentity,
                                    operatorIdentity = confirmation.operatorIdentity,
                                    confirmationReason = confirmation.confirmationReason,
                                    evidence = confirmation.evidence,
                                    transactionKind = confirmation.transactionKind,
                                    amount = confirmation.amount,
                                    currency = confirmation.currency,
                                    externalTransactionIdentity = confirmation.externalTransactionIdentity,
                                    paymentId = confirmation.paymentId,
                                    refundId = confirmation.refundId,
                                    confirmedAt = confirmation.confirmedAt,
                                )
                            },
                        )
                    },
                )
            },
        )
    }
}
