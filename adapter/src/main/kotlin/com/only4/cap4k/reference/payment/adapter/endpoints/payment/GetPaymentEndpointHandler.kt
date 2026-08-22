package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.queries.payment.read.GetPaymentQry
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentEndpoint
import org.springframework.stereotype.Component

@Component
class GetPaymentEndpointHandler : EndpointHandler<GetPaymentEndpoint.Request, GetPaymentEndpoint.Response> {
    override fun handle(request: GetPaymentEndpoint.Request): GetPaymentEndpoint.Response {
        val r = Mediator.queries.ask(GetPaymentQry.Request(request.paymentId))
        return GetPaymentEndpoint.Response(
            paymentId = r.paymentId, merchantId = r.merchantId, merchantOrderNumber = r.merchantOrderNumber,
            amount = r.amount, currency = r.currency, paymentMethod = r.paymentMethod, status = r.status,
            createdAt = r.createdAt, expiresAt = r.expiresAt, succeededAt = r.succeededAt,
            closedAt = r.closedAt, closeReason = r.closeReason, channelTransactionId = r.channelTransactionId,
            reservedRefundAmount = r.reservedRefundAmount, successfulRefundAmount = r.successfulRefundAmount,
            refundableAmount = r.refundableAmount, attemptCount = r.attemptCount,
            notificationReceiveCount = r.notificationReceiveCount,
            rejectedNotificationCount = r.rejectedNotificationCount,
            conflictingNotificationCount = r.conflictingNotificationCount,
            lastNotificationIdentity = r.lastNotificationIdentity,
            lastNotificationReceivedAt = r.lastNotificationReceivedAt,
            lastRejectionSummary = r.lastRejectionSummary, lastConflictSummary = r.lastConflictSummary,
            successFactFormed = r.successFactFormed,
            merchantOrderSuccessIdentity = r.merchantOrderSuccessIdentity,
            merchantSuccessNotificationIntentCount = r.merchantSuccessNotificationIntentCount,
            merchantSuccessNotificationIntentIdentity = r.merchantSuccessNotificationIntentIdentity,
            merchantSuccessNotificationIntentState = r.merchantSuccessNotificationIntentState,
            reviewCount = r.reviewCount, blockingReviewCount = r.blockingReviewCount,
            settlementEligible = r.settlementEligible, settlementBlocked = r.settlementBlocked,
            attempts = r.attempts.map { a ->
                GetPaymentEndpoint.Response.PaymentAttemptSummary(
                    paymentAttemptId = a.paymentAttemptId, channelId = a.channelId, status = a.status,
                    requestIdentity = a.requestIdentity, initiatedAt = a.initiatedAt,
                    channelTransactionId = a.channelTransactionId, finalResult = a.finalResult,
                    resultOccurredAt = a.resultOccurredAt, notificationReceiveCount = a.notificationReceiveCount,
                    notificationFirstReceivedAt = a.notificationFirstReceivedAt,
                    notificationLastReceivedAt = a.notificationLastReceivedAt,
                    verifiedNotificationCount = a.verifiedNotificationCount,
                    rejectedNotificationCount = a.rejectedNotificationCount,
                    conflictingNotificationCount = a.conflictingNotificationCount,
                    verdictSummary = a.verdictSummary, rejectionSummary = a.rejectionSummary,
                    conflictSummary = a.conflictSummary,
                    notificationReceipts = a.notificationReceipts.map { n ->
                        GetPaymentEndpoint.Response.NotificationReceiptSummary(
                            notificationIdentity = n.notificationIdentity, payloadIdentity = n.payloadIdentity,
                            channelId = n.channelId, channelTransactionId = n.channelTransactionId,
                            amount = n.amount, currency = n.currency, result = n.result, occurredAt = n.occurredAt,
                            firstReceivedAt = n.firstReceivedAt, lastReceivedAt = n.lastReceivedAt,
                            receiveCount = n.receiveCount, verified = n.verified, accepted = n.accepted,
                            decision = n.decision, verdictSummary = n.verdictSummary,
                            rejectionSummary = n.rejectionSummary, conflictSummary = n.conflictSummary,
                        )
                    },
                )
            },
            reviews = r.reviews.map { review ->
                GetPaymentEndpoint.Response.PaymentReviewSummary(
                    reviewId = review.reviewId, reviewIdentity = review.reviewIdentity,
                    type = review.type, status = review.status, openedAt = review.openedAt,
                    summary = review.summary, settlementImpact = review.settlementImpact,
                    resolvedAt = review.resolvedAt,
                    triggeringAttemptIdentities = review.triggeringAttemptIdentities,
                    triggeringReceiptIdentities = review.triggeringReceiptIdentities,
                    decisions = review.decisions.map { d ->
                        GetPaymentEndpoint.Response.PaymentReviewDecisionSummary(
                            decisionId = d.decisionId, decisionIdentity = d.decisionIdentity,
                            decision = d.decision, operatorIdentity = d.operatorIdentity,
                            operatorRole = d.operatorRole, authorizationOutcome = d.authorizationOutcome,
                            reason = d.reason, evidence = d.evidence, decidedAt = d.decidedAt,
                            eligibilityImpact = d.eligibilityImpact,
                            remediationReference = d.remediationReference,
                        )
                    },
                )
            },
        )
    }
}
