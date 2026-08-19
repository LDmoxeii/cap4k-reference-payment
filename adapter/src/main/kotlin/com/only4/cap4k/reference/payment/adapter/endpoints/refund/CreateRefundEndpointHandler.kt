package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.refund.create.CreateRefundCmd
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.CreateRefundEndpoint
import org.springframework.stereotype.Component

@Component
class CreateRefundEndpointHandler : EndpointHandler<CreateRefundEndpoint.Request, CreateRefundEndpoint.Response> {
    override fun handle(request: CreateRefundEndpoint.Request): CreateRefundEndpoint.Response {
        val response = Mediator.commands.send(
            CreateRefundCmd.Request(
                merchantId = request.merchantId,
                merchantRefundNumber = request.merchantRefundNumber,
                paymentId = request.paymentId,
                amount = request.amount,
                currency = request.currency,
                requestedAt = request.requestedAt,
            )
        )
        return CreateRefundEndpoint.Response(
            refundId = response.refundId,
            refundAttemptId = response.refundAttemptId,
            status = response.status,
            requestIdentity = response.requestIdentity,
            idempotentReplay = response.idempotentReplay,
            diagnosticSummary = response.diagnosticSummary,
        )
    }
}
