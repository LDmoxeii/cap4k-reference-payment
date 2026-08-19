package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.refund.result.ConfirmRefundResultCmd
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.ConfirmRefundResultEndpoint
import org.springframework.stereotype.Component

@Component
class ConfirmRefundResultEndpointHandler : EndpointHandler<ConfirmRefundResultEndpoint.Request, ConfirmRefundResultEndpoint.Response> {
    override fun handle(request: ConfirmRefundResultEndpoint.Request): ConfirmRefundResultEndpoint.Response {
        val outcome = Mediator.commands.send(
            ConfirmRefundResultCmd.Request(
                channelId = request.channelId,
                notificationId = request.notificationId,
                refundId = request.refundId,
                refundAttemptId = request.refundAttemptId,
                channelRefundId = request.channelRefundId,
                amount = request.amount,
                currency = request.currency,
                result = request.result,
                occurredAt = request.occurredAt,
                verificationMaterial = request.verificationMaterial,
            )
        ).outcome
        return ConfirmRefundResultEndpoint.Response(
            refundStatus = outcome.refundStatus.name,
            attemptStatus = outcome.attemptStatus?.name,
            notificationReceiveCount = outcome.notificationReceiveCount,
            disposition = outcome.disposition.name,
            duplicate = outcome.duplicate,
            accepted = outcome.accepted,
            rejected = outcome.rejected,
            conflicting = outcome.conflicting,
            reservationReleasedNow = outcome.reservationReleasedNow,
            reservationConvertedToSuccessNow = outcome.reservationConvertedToSuccessNow,
            reviewRequiredNow = outcome.reviewRequiredNow,
            rejectionSummary = outcome.rejectionSummary,
            conflictSummary = outcome.conflictSummary,
        )
    }
}
