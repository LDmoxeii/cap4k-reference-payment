package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RerunReconciliationBatchCmd
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.RerunReconciliationBatchEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class RerunReconciliationBatchEndpointHandler(
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<RerunReconciliationBatchEndpoint.Request, RerunReconciliationBatchEndpoint.Response> {
    override fun handle(request: RerunReconciliationBatchEndpoint.Request): RerunReconciliationBatchEndpoint.Response {
        val actor = actorContextResolver.consume()
        val response = Mediator.commands.send(
            RerunReconciliationBatchCmd.Request(
                ReconciliationBatchId.parse(request.batchId),
                actor.actorId,
                request.idempotencyKey,
                requestedAt = Instant.now(clock),
            )
        )
        return RerunReconciliationBatchEndpoint.Response(
            batchId = response.reconciliationBatchId.toString(),
            runId = response.runId,
            status = response.batchStatus,
            idempotentReplay = response.idempotentReplay,
            statementIdentity = response.statementIdentity,
            statementRevision = response.statementRevision,
            receipt = response.receipt,
        )
    }
}
