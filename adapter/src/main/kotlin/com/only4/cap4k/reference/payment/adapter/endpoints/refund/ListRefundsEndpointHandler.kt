package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.application.queries.refund.read.RefundReadModel
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.ListRefundsEndpoint
import org.springframework.stereotype.Component

@Component
class ListRefundsEndpointHandler(
    private val readModel: RefundReadModel,
) : EndpointHandler<ListRefundsEndpoint.Request, ListRefundsEndpoint.Response> {
    override fun handle(request: ListRefundsEndpoint.Request): ListRefundsEndpoint.Response = readModel.list(request)
}
