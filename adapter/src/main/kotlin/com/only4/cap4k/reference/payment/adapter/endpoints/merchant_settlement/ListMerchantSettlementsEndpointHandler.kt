package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read.MerchantSettlementReadModel
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ListMerchantSettlementsEndpoint
import org.springframework.stereotype.Component

@Component
class ListMerchantSettlementsEndpointHandler(
    private val readModel: MerchantSettlementReadModel,
) : EndpointHandler<ListMerchantSettlementsEndpoint.Request, ListMerchantSettlementsEndpoint.Response> {
    override fun handle(request: ListMerchantSettlementsEndpoint.Request): ListMerchantSettlementsEndpoint.Response = readModel.list(request)
}
