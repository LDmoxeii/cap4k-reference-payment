package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.application.queries.payment.read.PaymentIntentReadModel
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.ListPaymentIntentsEndpoint
import org.springframework.stereotype.Component

@Component
class ListPaymentIntentsEndpointHandler(
    private val readModel: PaymentIntentReadModel,
) : EndpointHandler<ListPaymentIntentsEndpoint.Request, ListPaymentIntentsEndpoint.Response> {
    override fun handle(request: ListPaymentIntentsEndpoint.Request): ListPaymentIntentsEndpoint.Response = readModel.list(request)
}
