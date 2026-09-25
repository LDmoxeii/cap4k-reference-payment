package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.application.queries.payment.read.PaymentChannelResultReceiptReadModel
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentChannelResultReceiptsEndpoint
import org.springframework.stereotype.Component

@Component
class GetPaymentChannelResultReceiptsEndpointHandler(
    private val readModel: PaymentChannelResultReceiptReadModel,
) : EndpointHandler<GetPaymentChannelResultReceiptsEndpoint.Request, GetPaymentChannelResultReceiptsEndpoint.Response> {
    override fun handle(
        request: GetPaymentChannelResultReceiptsEndpoint.Request,
    ): GetPaymentChannelResultReceiptsEndpoint.Response = readModel.get(request)
}
