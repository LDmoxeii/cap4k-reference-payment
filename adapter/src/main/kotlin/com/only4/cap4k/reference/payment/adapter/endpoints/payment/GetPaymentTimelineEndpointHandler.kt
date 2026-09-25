package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.application.queries.payment.read.PaymentTimelineReadModel
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentTimelineEndpoint
import org.springframework.stereotype.Component

@Component
class GetPaymentTimelineEndpointHandler(
    private val readModel: PaymentTimelineReadModel,
) : EndpointHandler<GetPaymentTimelineEndpoint.Request, GetPaymentTimelineEndpoint.Response> {
    override fun handle(request: GetPaymentTimelineEndpoint.Request): GetPaymentTimelineEndpoint.Response =
        readModel.get(request)
}
