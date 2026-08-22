package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.result.ConfirmPaymentResultCmd
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.ConfirmPaymentResultEndpoint
import org.springframework.stereotype.Component

@Component
class ConfirmPaymentResultEndpointHandler : EndpointHandler<ConfirmPaymentResultEndpoint.Request, ConfirmPaymentResultEndpoint.Response> {
    override fun handle(request: ConfirmPaymentResultEndpoint.Request): ConfirmPaymentResultEndpoint.Response {
        val outcome = Mediator.commands.send(
            ConfirmPaymentResultCmd.Request(
                channelId = request.channelId,
                notificationId = request.notificationId,
                paymentId = request.paymentId,
                paymentAttemptId = request.paymentAttemptId,
                channelTransactionId = request.channelTransactionId,
                amount = request.amount,
                currency = request.currency,
                result = request.result,
                occurredAt = request.occurredAt,
                verificationMaterial = request.verificationMaterial,
            )
        ).outcome
        return ConfirmPaymentResultEndpoint.Response(
            paymentStatus = outcome.paymentStatus.name,
            attemptStatus = outcome.attemptStatus?.name,
            notificationReceiveCount = outcome.notificationReceiveCount,
            disposition = outcome.disposition.name,
            duplicate = outcome.duplicate,
            accepted = outcome.accepted,
            rejected = outcome.rejected,
            conflicting = outcome.conflicting,
            rejectionSummary = outcome.rejectionSummary,
            conflictSummary = outcome.conflictSummary,
            successFactFormedNow = outcome.successFactFormedNow,
            reviewIdentity = outcome.reviewIdentity,
            settlementEligible = outcome.settlementEligible,
            notificationIntentState = outcome.notificationIntentState?.name,
        )
    }
}
