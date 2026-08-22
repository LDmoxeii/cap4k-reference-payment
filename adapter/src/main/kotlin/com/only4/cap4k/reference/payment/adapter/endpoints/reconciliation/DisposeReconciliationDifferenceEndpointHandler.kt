package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.reconciliation.disposition.DisposeReconciliationDifferenceCmd
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.DisposeReconciliationDifferenceEndpoint
import org.springframework.stereotype.Component

@Component
class DisposeReconciliationDifferenceEndpointHandler : EndpointHandler<DisposeReconciliationDifferenceEndpoint.Request, DisposeReconciliationDifferenceEndpoint.Response> {
    override fun handle(request: DisposeReconciliationDifferenceEndpoint.Request): DisposeReconciliationDifferenceEndpoint.Response {
        val response = Mediator.commands.send(
            DisposeReconciliationDifferenceCmd.Request(
                batchId = request.batchId,
                itemId = request.itemId,
                merchantId = request.merchantId,
                channelId = request.channelId,
                operatorIdentity = request.operatorIdentity,
                operatorRole = request.operatorRole,
                conclusion = request.conclusion,
                settlementImpact = request.settlementImpact,
                evidence = request.evidence,
                followUp = request.followUp,
                disposedAt = request.disposedAt,
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
        )
    }
}
