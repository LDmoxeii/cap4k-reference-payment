package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.attempt.CreatePaymentAttemptCmd
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.CreatePaymentAttemptEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import org.springframework.stereotype.Component

@Component
class CreatePaymentAttemptEndpointHandler :
    EndpointHandler<CreatePaymentAttemptEndpoint.Request, CreatePaymentAttemptEndpoint.Response> {
    override fun handle(request: CreatePaymentAttemptEndpoint.Request): CreatePaymentAttemptEndpoint.Response {
        val response = Mediator.commands.send(
            CreatePaymentAttemptCmd.Request(
                paymentId = PaymentId.parse(request.paymentId),
                idempotencyKey = request.idempotencyKey,
                riskReason = request.riskReason,
            ),
        )
        return CreatePaymentAttemptEndpoint.Response(
            paymentId = response.paymentId,
            paymentAttemptId = response.paymentAttemptId,
            paymentStatus = publicPaymentStatus(response.paymentStatus),
            attemptStatus = response.attemptStatus,
            channelId = response.channelId,
            requestIdentity = response.requestIdentity,
            interactionInformation = response.interactionInformation,
            riskReason = response.riskReason,
            receipt = response.receipt,
        )
    }
}

internal fun publicPaymentStatus(status: String): String = when (status) {
    "PENDING" -> "PAYABLE"
    else -> status
}
