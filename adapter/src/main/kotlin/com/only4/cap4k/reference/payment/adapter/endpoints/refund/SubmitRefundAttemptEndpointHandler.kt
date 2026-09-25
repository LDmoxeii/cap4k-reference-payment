package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.contract.ReferenceContractStatusMapper
import com.only4.cap4k.reference.payment.application.commands.refund.attempt.SubmitRefundAttemptCmd
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.SubmitRefundAttemptEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import org.springframework.stereotype.Component

@Component
class SubmitRefundAttemptEndpointHandler : EndpointHandler<SubmitRefundAttemptEndpoint.Request, SubmitRefundAttemptEndpoint.Response> {
    override fun handle(request: SubmitRefundAttemptEndpoint.Request): SubmitRefundAttemptEndpoint.Response {
        val response = Mediator.commands.send(
            SubmitRefundAttemptCmd.Request(
                refundId = RefundId.parse(request.refundId),
                refundAttemptId = RefundAttemptId.parse(request.refundAttemptId),
                idempotencyKey = request.idempotencyKey,
            ),
        )
        return SubmitRefundAttemptEndpoint.Response(
            refundId = response.refundId,
            refundAttemptId = response.refundAttemptId,
            refundStatus = ReferenceContractStatusMapper.refundStatus(response.refundStatus),
            finality = ReferenceContractStatusMapper.refundFinality(response.refundStatus),
            attemptStatus = ReferenceContractStatusMapper.refundAttemptStatus(response.attemptStatus),
            channelId = response.channelId,
            requestIdentity = response.requestIdentity,
            diagnosticSummary = response.diagnosticSummary,
            receipt = response.receipt,
        )
    }
}
