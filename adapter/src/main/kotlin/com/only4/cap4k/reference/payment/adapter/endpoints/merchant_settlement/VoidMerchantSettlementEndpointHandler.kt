package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.voiding.VoidMerchantSettlementCmd
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.VoidMerchantSettlementEndpoint
import org.springframework.stereotype.Component

@Component
class VoidMerchantSettlementEndpointHandler : EndpointHandler<VoidMerchantSettlementEndpoint.Request, VoidMerchantSettlementEndpoint.Response> {
    override fun handle(request: VoidMerchantSettlementEndpoint.Request): VoidMerchantSettlementEndpoint.Response {
        val response = Mediator.commands.send(
            VoidMerchantSettlementCmd.Request(
                settlementId = request.settlementId,
                operatorIdentity = request.operatorIdentity,
                operatorRole = request.operatorRole,
                reason = request.reason,
                voidedAt = request.voidedAt,
                createReplacement = request.createReplacement,
            )
        )
        return VoidMerchantSettlementEndpoint.Response(response.settlementId, response.status, response.replacementSettlementId)
    }
}
