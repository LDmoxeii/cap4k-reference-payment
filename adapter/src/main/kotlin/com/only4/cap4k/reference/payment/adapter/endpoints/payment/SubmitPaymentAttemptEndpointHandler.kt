package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.attempt.SubmitPaymentAttemptCmd
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.SubmitPaymentAttemptEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import org.springframework.stereotype.Component

@Component
class SubmitPaymentAttemptEndpointHandler :
    EndpointHandler<SubmitPaymentAttemptEndpoint.Request, SubmitPaymentAttemptEndpoint.Response> {
    override fun handle(request: SubmitPaymentAttemptEndpoint.Request): SubmitPaymentAttemptEndpoint.Response {
        val response = Mediator.commands.send(
            SubmitPaymentAttemptCmd.Request(
                paymentId = PaymentId.parse(request.paymentId),
                paymentAttemptId = PaymentAttemptId.parse(request.paymentAttemptId),
                idempotencyKey = request.idempotencyKey,
            ),
        )
        return SubmitPaymentAttemptEndpoint.Response(
            paymentId = response.paymentId,
            paymentAttemptId = response.paymentAttemptId,
            paymentStatus = publicPaymentStatus(response.paymentStatus),
            attemptStatus = response.attemptStatus,
            submissionIdentity = requireNotNull(response.submissionIdentity),
            channelId = response.channelId,
            interactionInformation = response.interactionInformation,
            submissionOutcome = requireNotNull(response.submissionOutcome),
            channelReference = response.channelReference,
            diagnosticSummary = response.diagnosticSummary,
            receipt = response.receipt,
        )
    }
}
