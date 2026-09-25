package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toContractMoney
import com.only4.cap4k.reference.payment.adapter.application.queries.reconciliation.read.ReconciliationRunReadModel
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import com.only4.cap4k.reference.payment.application.commands.reconciliation.disposition.DisposeReconciliationDifferenceCmd
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RerunReconciliationBatchCmd
import com.only4.cap4k.reference.payment.application.queries.reconciliation.read.GetReconciliationBatchQry
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.ConfirmReconciliationFactEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.DisposeReconciliationRunDifferenceEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.GetReconciliationBatchEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.GetReconciliationRunEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.ListReconciliationRunsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.RerunReconciliationRunEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationBatchStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationRunStatus
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class GetReconciliationRunEndpointHandler(
    private val readModel: ReconciliationRunReadModel,
) : EndpointHandler<GetReconciliationRunEndpoint.Request, GetReconciliationRunEndpoint.Response> {
    override fun handle(request: GetReconciliationRunEndpoint.Request): GetReconciliationRunEndpoint.Response {
        val batch = Mediator.queries.ask(
            GetReconciliationBatchQry.Request(ReconciliationBatchId.parse(readModel.batchIdFor(request.runId))),
        )
        val run = batch.runs.firstOrNull { it.runId == request.runId }
            ?: throw IllegalArgumentException("未找到对账运行：${request.runId}")
        val batchStatus = ReconciliationBatchStatus.valueOf(batch.status).value
        val runStatus = ReconciliationRunStatus.valueOf(run.status)
        val effective = batch.currentEffectiveRunId == run.runId
        return GetReconciliationRunEndpoint.Response(
            runId = run.runId,
            channelId = batch.channelId,
            currency = batch.currency,
            businessDate = batch.reconciliationDate,
            businessTimezone = batch.businessTimezone,
            billIdentity = run.statementIdentity,
            billRevision = run.statementRevision,
            statementCompleteness = run.statementCompleteness,
            status = readModel.publicStatus(runStatus, batchStatus, effective),
            finality = readModel.finality(runStatus, batchStatus),
            effectiveRun = effective,
            sortTime = readModel.createdAtFor(run.runId),
            fetchedAt = run.fetchedAt,
            startedAt = run.startedAt,
            completedAt = run.completedAt,
            matchedCount = run.matchedCount,
            differenceCount = run.differenceCount,
            unresolvedDifferenceCount = run.unresolvedDifferenceCount,
            settlementBlocked = if (effective) batch.settlementBlocked else run.unresolvedDifferenceCount > 0,
            blockingReason = if (effective) batch.blockingReason else run.failureSummary,
            failureSummary = run.failureSummary,
            differences = run.items.map(::toContractItem),
        )
    }
}

@Component
class ListReconciliationRunsEndpointHandler(
    private val readModel: ReconciliationRunReadModel,
) : EndpointHandler<ListReconciliationRunsEndpoint.Request, ListReconciliationRunsEndpoint.Response> {
    override fun handle(request: ListReconciliationRunsEndpoint.Request): ListReconciliationRunsEndpoint.Response =
        readModel.list(request)
}

@Component
class RerunReconciliationRunEndpointHandler(
    private val readModel: ReconciliationRunReadModel,
    private val clock: Clock,
) : EndpointHandler<RerunReconciliationRunEndpoint.Request, RerunReconciliationRunEndpoint.Response> {
    override fun handle(request: RerunReconciliationRunEndpoint.Request): RerunReconciliationRunEndpoint.Response {
        val response = Mediator.commands.send(
            RerunReconciliationBatchCmd.Request(
                reconciliationBatchId = ReconciliationBatchId.parse(readModel.batchIdFor(request.runId)),
                requestedBy = REFERENCE_SYSTEM_ACTOR_ID,
                idempotencyKey = request.idempotencyKey,
                sourceRunId = request.runId,
                requestedAt = Instant.now(clock),
            ),
        )
        val returnedRun = response.runId?.let { runId ->
            readModel.list(
                ListReconciliationRunsEndpoint.Request(merchantId = "", runId = runId, pageSize = 1),
            ).items.singleOrNull()
        }
        return RerunReconciliationRunEndpoint.Response(
            runId = response.runId,
            status = returnedRun?.status ?: response.batchStatus,
            finality = returnedRun?.finality ?: Finality.REVIEW_REQUIRED,
            idempotentReplay = response.idempotentReplay,
            billIdentity = response.statementIdentity,
            billRevision = response.statementRevision,
            receipt = response.receipt,
        )
    }

    private companion object {
        const val REFERENCE_SYSTEM_ACTOR_ID = "reference-system"
    }

}

@Component
class DisposeReconciliationRunDifferenceEndpointHandler(
    private val readModel: ReconciliationRunReadModel,
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<DisposeReconciliationRunDifferenceEndpoint.Request, DisposeReconciliationRunDifferenceEndpoint.Response> {
    override fun handle(request: DisposeReconciliationRunDifferenceEndpoint.Request): DisposeReconciliationRunDifferenceEndpoint.Response {
        val actor = actorContextResolver.consume()
        readModel.requireRunItem(request.runId, request.itemId)
        val now = Instant.now(clock)
        val response = Mediator.commands.send(
            DisposeReconciliationDifferenceCmd.Request(
                reconciliationBatchId = ReconciliationBatchId.parse(readModel.batchIdFor(request.runId)),
                itemId = request.itemId,
                merchantId = request.merchantId,
                channelId = request.channelId,
                operatorIdentity = actor.actorId,
                idempotencyKey = request.idempotencyKey,
                runId = request.runId,
                operatorRole = actor.role,
                conclusion = request.conclusion,
                settlementImpact = request.settlementImpact,
                evidence = request.evidence,
                reason = request.reason,
                followUp = request.followUp,
                disposedAt = now,
            ),
        )
        return DisposeReconciliationRunDifferenceEndpoint.Response(
            dispositionId = response.dispositionId,
            actorId = response.actorId,
            reason = response.reason,
            evidence = response.evidence,
            decidedAt = response.disposedAt,
            status = response.status,
            authorization = response.authorization,
            confirmationFactId = response.confirmationFactId,
            settlementBlocked = response.settlementBlocked,
            blockingReason = response.blockingReason,
            receipt = response.receipt,
        )
    }
}

@Component
class ConfirmReconciliationFactEndpointHandler(
    private val readModel: ReconciliationRunReadModel,
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<ConfirmReconciliationFactEndpoint.Request, ConfirmReconciliationFactEndpoint.Response> {
    override fun handle(request: ConfirmReconciliationFactEndpoint.Request): ConfirmReconciliationFactEndpoint.Response {
        val actor = actorContextResolver.consume()
        readModel.requireRunItem(request.runId, request.itemId)
        val now = Instant.now(clock)
        val response = Mediator.commands.send(
            DisposeReconciliationDifferenceCmd.Request(
                reconciliationBatchId = ReconciliationBatchId.parse(readModel.batchIdFor(request.runId)),
                itemId = request.itemId,
                merchantId = request.merchantId,
                channelId = request.channelId,
                operatorIdentity = actor.actorId,
                idempotencyKey = request.idempotencyKey,
                runId = request.runId,
                confirmationOnly = true,
                operatorRole = actor.role,
                conclusion = "CONFIRM_PLATFORM_FACT",
                settlementImpact = "CONFIRMS_SETTLEMENT_FACT",
                evidence = request.evidence,
                reason = request.reason,
                followUp = request.reason,
                disposedAt = now,
            ),
        )
        return ConfirmReconciliationFactEndpoint.Response(
            confirmationFactId = requireNotNull(response.confirmationFactId) { "确认事实未创建" },
            actorId = response.actorId,
            reason = response.reason,
            evidence = response.evidence,
            recordedAt = response.disposedAt,
            dispositionId = response.dispositionId,
            settlementBlocked = response.settlementBlocked,
            blockingReason = response.blockingReason,
            receipt = response.receipt,
        )
    }
}

private fun toContractItem(item: GetReconciliationBatchQry.Response.ReconciliationItemSummary): GetReconciliationBatchEndpoint.Response.ReconciliationItemSummary =
    GetReconciliationBatchEndpoint.Response.ReconciliationItemSummary(
        itemId = item.itemId,
        differenceIdentity = item.differenceIdentity,
        transactionKind = item.transactionKind,
        differenceType = item.differenceType,
        channelRecordIdentity = item.channelRecordIdentity,
        channelTransactionIdentity = item.channelTransactionIdentity,
        channelMoney = item.channelAmount?.toContractMoney(
            requireNotNull(item.channelCurrency) { "渠道金额缺少币种" },
        ),
        channelRawStatus = item.channelRawStatus,
        channelOccurredAt = item.channelOccurredAt,
        channelReceivedAt = item.channelReceivedAt,
        platformFactIdentity = item.platformFactIdentity,
        paymentId = item.paymentId?.toString(),
        paymentAttemptId = item.paymentAttemptId,
        refundId = item.refundId?.toString(),
        refundAttemptId = item.refundAttemptId,
        platformTransactionIdentity = item.platformTransactionIdentity,
        platformMoney = item.platformAmount?.toContractMoney(
            requireNotNull(item.platformCurrency) { "平台金额缺少币种" },
        ),
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
                reason = disposition.reason,
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
                money = confirmation.amount.toContractMoney(confirmation.currency),
                externalTransactionIdentity = confirmation.externalTransactionIdentity,
                paymentId = confirmation.paymentId?.toString(),
                refundId = confirmation.refundId?.toString(),
                confirmedAt = confirmation.confirmedAt,
            )
        },
    )
