package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.create.CreatePaymentCmd
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.CreatePaymentEndpoint
import org.springframework.stereotype.Component

@Component
class CreatePaymentEndpointHandler : EndpointHandler<CreatePaymentEndpoint.Request, CreatePaymentEndpoint.Response> {
    override fun handle(request: CreatePaymentEndpoint.Request): CreatePaymentEndpoint.Response {
        val response = Mediator.commands.send(
            CreatePaymentCmd.Request(
                merchantId = request.merchantId,
                merchantOrderNumber = request.merchantOrderNumber,
                idempotencyKey = request.idempotencyKey,
                amount = request.amount,
                currency = request.currency,
                paymentMethod = request.paymentMethod,
                expiresAt = request.expiresAt,
            )
        )
        return CreatePaymentEndpoint.Response(
            paymentId = response.paymentId,
            status = response.status,
            idempotentReplay = response.idempotentReplay,
            rejectionCode = response.rejectionCode,
            rejectionSummary = response.rejectionSummary,
        )
    }
}
