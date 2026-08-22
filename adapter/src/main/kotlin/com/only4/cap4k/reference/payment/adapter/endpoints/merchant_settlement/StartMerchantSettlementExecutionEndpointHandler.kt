package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.execution.StartMerchantSettlementExecutionCmd
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.StartMerchantSettlementExecutionEndpoint
import org.springframework.stereotype.Component

@Component
class StartMerchantSettlementExecutionEndpointHandler : EndpointHandler<StartMerchantSettlementExecutionEndpoint.Request, StartMerchantSettlementExecutionEndpoint.Response> {
    override fun handle(request: StartMerchantSettlementExecutionEndpoint.Request): StartMerchantSettlementExecutionEndpoint.Response {
        val response = Mediator.commands.send(
            StartMerchantSettlementExecutionCmd.Request(
                settlementId = request.settlementId,
                operatorIdentity = request.operatorIdentity,
                operatorRole = request.operatorRole,
                requestedAt = request.requestedAt,
            )
        )
        return StartMerchantSettlementExecutionEndpoint.Response(
            settlementId = response.settlementId,
            attemptId = response.attemptId,
            executionGroupIdentity = response.executionGroupIdentity,
            requestIdentity = response.requestIdentity,
            status = response.status,
            providerAccepted = response.providerAccepted,
            diagnosticSummary = response.diagnosticSummary,
        )
    }
}
