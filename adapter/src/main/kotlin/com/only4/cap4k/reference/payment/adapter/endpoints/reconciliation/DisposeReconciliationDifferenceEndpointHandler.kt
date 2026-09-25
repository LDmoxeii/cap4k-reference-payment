package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.reconciliation.disposition.DisposeReconciliationDifferenceCmd
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.DisposeReconciliationDifferenceEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class DisposeReconciliationDifferenceEndpointHandler(
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<DisposeReconciliationDifferenceEndpoint.Request, DisposeReconciliationDifferenceEndpoint.Response> {
    override fun handle(request: DisposeReconciliationDifferenceEndpoint.Request): DisposeReconciliationDifferenceEndpoint.Response {
        val actor = actorContextResolver.consume()
        val response = Mediator.commands.send(
            DisposeReconciliationDifferenceCmd.Request(
                reconciliationBatchId = ReconciliationBatchId.parse(request.batchId),
                itemId = request.itemId,
                merchantId = request.merchantId,
                channelId = request.channelId,
                operatorIdentity = actor.actorId,
                idempotencyKey = request.idempotencyKey,
                operatorRole = actor.role,
                conclusion = request.conclusion,
                settlementImpact = request.settlementImpact,
                evidence = request.evidence,
                reason = request.reason,
                followUp = request.followUp,
                disposedAt = Instant.now(clock),
            )
        )
        return DisposeReconciliationDifferenceEndpoint.Response(
            dispositionId = response.dispositionId,
            authorization = response.authorization,
            status = response.status,
            confirmationFactId = response.confirmationFactId,
            batchStatus = response.batchStatus,
            settlementBlocked = response.settlementBlocked,
            blockingReason = response.blockingReason,
            actorId = response.actorId,
            receipt = response.receipt,
        )
    }
}
