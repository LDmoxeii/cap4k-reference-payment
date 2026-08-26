package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.confirm.ConfirmMerchantSettlementCmd
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ConfirmMerchantSettlementEndpoint
import org.springframework.stereotype.Component

@Component
class ConfirmMerchantSettlementEndpointHandler : EndpointHandler<ConfirmMerchantSettlementEndpoint.Request, ConfirmMerchantSettlementEndpoint.Response> {
    override fun handle(request: ConfirmMerchantSettlementEndpoint.Request): ConfirmMerchantSettlementEndpoint.Response {
        val response = Mediator.commands.send(
            ConfirmMerchantSettlementCmd.Request(
                merchantSettlementId = MerchantSettlementId.parse(request.settlementId),
                operatorIdentity = request.operatorIdentity,
                operatorRole = request.operatorRole,
                confirmedAt = request.confirmedAt,
            )
        )
        return ConfirmMerchantSettlementEndpoint.Response(response.merchantSettlementId.toString(), response.status, response.netAmount)
    }
}
