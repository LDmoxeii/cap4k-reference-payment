package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.contract.ReferenceContractStatusMapper
import com.only4.cap4k.reference.payment.application.commands.refund.attempt.CreateRefundAttemptCmd
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.CreateRefundAttemptEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import org.springframework.stereotype.Component

@Component
class CreateRefundAttemptEndpointHandler : EndpointHandler<CreateRefundAttemptEndpoint.Request, CreateRefundAttemptEndpoint.Response> {
    override fun handle(request: CreateRefundAttemptEndpoint.Request): CreateRefundAttemptEndpoint.Response {
        val response = Mediator.commands.send(
            CreateRefundAttemptCmd.Request(
                refundId = RefundId.parse(request.refundId),
                idempotencyKey = request.idempotencyKey,
            ),
        )
        return CreateRefundAttemptEndpoint.Response(
            refundId = response.refundId,
            refundAttemptId = response.refundAttemptId,
            refundStatus = ReferenceContractStatusMapper.refundStatus(response.refundStatus),
            finality = ReferenceContractStatusMapper.refundFinality(response.refundStatus),
            attemptStatus = ReferenceContractStatusMapper.refundAttemptStatus(response.attemptStatus),
            channelId = response.channelId,
            requestIdentity = response.requestIdentity,
            reusedExistingAttempt = response.reusedExistingAttempt,
            receipt = response.receipt,
        )
    }
}
