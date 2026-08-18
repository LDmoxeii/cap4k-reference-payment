package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.attempt.StartPaymentAttemptCmd
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.StartPaymentAttemptEndpoint
import org.springframework.stereotype.Component

@Component
class StartPaymentAttemptEndpointHandler : EndpointHandler<StartPaymentAttemptEndpoint.Request, StartPaymentAttemptEndpoint.Response> {
    override fun handle(request: StartPaymentAttemptEndpoint.Request): StartPaymentAttemptEndpoint.Response {
        val response = Mediator.commands.send(StartPaymentAttemptCmd.Request(request.paymentId))
        return StartPaymentAttemptEndpoint.Response(
            paymentAttemptId = response.paymentAttemptId,
            channelId = response.channelId,
            requestIdentity = response.requestIdentity,
            paymentStatus = response.paymentStatus,
            attemptStatus = response.attemptStatus,
        )
    }
}
