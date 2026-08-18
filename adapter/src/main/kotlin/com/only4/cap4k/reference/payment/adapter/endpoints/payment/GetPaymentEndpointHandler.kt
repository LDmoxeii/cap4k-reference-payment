package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.queries.payment.read.GetPaymentQry
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentEndpoint
import org.springframework.stereotype.Component

@Component
class GetPaymentEndpointHandler : EndpointHandler<GetPaymentEndpoint.Request, GetPaymentEndpoint.Response> {
    override fun handle(request: GetPaymentEndpoint.Request): GetPaymentEndpoint.Response {
        val response = Mediator.queries.ask(GetPaymentQry.Request(request.paymentId))
        return GetPaymentEndpoint.Response(
            paymentId = response.paymentId,
            merchantId = response.merchantId,
            merchantOrderNumber = response.merchantOrderNumber,
            amount = response.amount,
            currency = response.currency,
            paymentMethod = response.paymentMethod,
            status = response.status,
            createdAt = response.createdAt,
            expiresAt = response.expiresAt,
            succeededAt = response.succeededAt,
            channelTransactionId = response.channelTransactionId,
            attemptCount = response.attemptCount,
            notificationReceiveCount = response.notificationReceiveCount,
            rejectedNotificationCount = response.rejectedNotificationCount,
            conflictingNotificationCount = response.conflictingNotificationCount,
            lastNotificationIdentity = response.lastNotificationIdentity,
            lastNotificationReceivedAt = response.lastNotificationReceivedAt,
            lastRejectionSummary = response.lastRejectionSummary,
            lastConflictSummary = response.lastConflictSummary,
            successFactFormed = response.successFactFormed,
            merchantSuccessNotificationIntentCount = response.merchantSuccessNotificationIntentCount,
            settlementBlocked = response.settlementBlocked,
            attempts = response.attempts.map { attempt ->
                GetPaymentEndpoint.Response.PaymentAttemptSummary(
                    paymentAttemptId = attempt.paymentAttemptId,
                    channelId = attempt.channelId,
                    status = attempt.status,
                    requestIdentity = attempt.requestIdentity,
                    initiatedAt = attempt.initiatedAt,
                    channelTransactionId = attempt.channelTransactionId,
                    finalResult = attempt.finalResult,
                    resultOccurredAt = attempt.resultOccurredAt,
                    notificationReceiveCount = attempt.notificationReceiveCount,
                    notificationFirstReceivedAt = attempt.notificationFirstReceivedAt,
                    notificationLastReceivedAt = attempt.notificationLastReceivedAt,
                    verifiedNotificationCount = attempt.verifiedNotificationCount,
                    rejectedNotificationCount = attempt.rejectedNotificationCount,
                    conflictingNotificationCount = attempt.conflictingNotificationCount,
                    verdictSummary = attempt.verdictSummary,
                    rejectionSummary = attempt.rejectionSummary,
                    conflictSummary = attempt.conflictSummary,
                    notificationReceipts = attempt.notificationReceipts.map { receipt ->
                        GetPaymentEndpoint.Response.NotificationReceiptSummary(
                            notificationIdentity = receipt.notificationIdentity,
                            channelId = receipt.channelId,
                            channelTransactionId = receipt.channelTransactionId,
                            amount = receipt.amount,
                            currency = receipt.currency,
                            result = receipt.result,
                            occurredAt = receipt.occurredAt,
                            firstReceivedAt = receipt.firstReceivedAt,
                            lastReceivedAt = receipt.lastReceivedAt,
                            receiveCount = receipt.receiveCount,
                            verified = receipt.verified,
                            accepted = receipt.accepted,
                            decision = receipt.decision,
                            verdictSummary = receipt.verdictSummary,
                            rejectionSummary = receipt.rejectionSummary,
                            conflictSummary = receipt.conflictSummary,
                        )
                    },
                )
            },
        )
    }
}
