package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RerunReconciliationBatchCmd
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.RerunReconciliationBatchEndpoint
import org.springframework.stereotype.Component

@Component
class RerunReconciliationBatchEndpointHandler : EndpointHandler<RerunReconciliationBatchEndpoint.Request, RerunReconciliationBatchEndpoint.Response> {
    override fun handle(request: RerunReconciliationBatchEndpoint.Request): RerunReconciliationBatchEndpoint.Response {
        val response = Mediator.commands.send(
            RerunReconciliationBatchCmd.Request(request.batchId, request.requestedBy, request.requestedAt)
        )
        return RerunReconciliationBatchEndpoint.Response(
            batchId = response.batchId,
            runId = response.runId,
            status = response.batchStatus,
            idempotentReplay = response.idempotentReplay,
            statementIdentity = response.statementIdentity,
            statementRevision = response.statementRevision,
        )
    }
}
