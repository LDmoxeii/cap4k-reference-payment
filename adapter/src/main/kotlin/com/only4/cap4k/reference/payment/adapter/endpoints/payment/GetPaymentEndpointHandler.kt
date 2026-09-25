package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.contract.ReferenceContractStatusMapper
import com.only4.cap4k.reference.payment.adapter.endpoints.toContractMoney
import com.only4.cap4k.reference.payment.application.queries.payment.read.GetPaymentQry
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentEndpoint
import org.springframework.stereotype.Component

@Component
class GetPaymentEndpointHandler : EndpointHandler<GetPaymentEndpoint.Request, GetPaymentEndpoint.Response> {
    override fun handle(request: GetPaymentEndpoint.Request): GetPaymentEndpoint.Response {
        val r = Mediator.queries.ask(GetPaymentQry.Request(PaymentId.parse(request.paymentId)))
        return GetPaymentEndpoint.Response(
            paymentId = r.paymentId.toString(), merchantId = r.merchantId, merchantOrderNumber = r.merchantOrderNumber,
            money = r.amount.toContractMoney(r.currency), paymentMethod = r.paymentMethod, status = publicPaymentStatus(r.status),
            finality = ReferenceContractStatusMapper.paymentFinality(r.status, r.settlementBlocked, r.blockingReviewCount),
            createdAt = r.createdAt, expiresAt = r.expiresAt, succeededAt = r.succeededAt,
            closedAt = r.closedAt, closeReason = r.closeReason, channelTransactionId = r.channelTransactionId,
            feeSnapshot = r.feeSnapshot?.let { fee ->
                GetPaymentEndpoint.Response.FeeSnapshot(
                    feeRate = fee.feeRate, basisPoints = fee.basisPoints,
                    fixedFeeMoney = fee.fixedFeeAmount.toContractMoney(r.currency),
                    roundingMode = fee.roundingMode, currencyPrecision = fee.currencyPrecision,
                    calculationMoney = fee.calculationAmount.toContractMoney(r.currency),
                    feeMoney = fee.feeAmount.toContractMoney(r.currency), formedAt = fee.formedAt,
                )
            },
            refundBudget = GetPaymentEndpoint.Response.RefundBudget(
                originalAmount = r.amount.toContractMoney(r.currency),
                succeededAmount = r.successfulRefundAmount.toContractMoney(r.currency),
                reservedAmount = r.reservedRefundAmount.toContractMoney(r.currency),
                availableAmount = r.refundableAmount.toContractMoney(r.currency),
            ),
            attemptCount = r.attemptCount,
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
                    submissionIdentity = a.submissionIdentity, submittedAt = a.submittedAt,
                    acceptedAt = a.acceptedAt, completedAt = a.completedAt,
                    interactionInformation = a.interactionInformation, riskReason = a.riskReason,
                    channelTransactionId = a.channelTransactionId, finalResult = a.finalResult,
                    resultOccurredAt = a.resultOccurredAt, notificationReceiveCount = a.notificationReceiveCount,
                    notificationFirstReceivedAt = a.notificationFirstReceivedAt,
                    notificationLastReceivedAt = a.notificationLastReceivedAt,
                    verifiedNotificationCount = a.verifiedNotificationCount,
                    rejectedNotificationCount = a.rejectedNotificationCount,
                    conflictingNotificationCount = a.conflictingNotificationCount,
                    verdictSummary = a.verdictSummary, rejectionSummary = a.rejectionSummary,
                    conflictSummary = a.conflictSummary,
                    submissionReceipts = a.submissionReceipts.map { s ->
                        GetPaymentEndpoint.Response.SubmissionReceiptSummary(
                            submissionIdentity = s.submissionIdentity, requestIdentity = s.requestIdentity,
                            channelId = s.channelId, submittedAt = s.submittedAt, outcome = s.outcome,
                            channelReference = s.channelReference, diagnosticSummary = s.diagnosticSummary,
                        )
                    },
                    notificationReceipts = a.notificationReceipts.map { n ->
                        GetPaymentEndpoint.Response.NotificationReceiptSummary(
                            notificationIdentity = n.notificationIdentity, payloadIdentity = n.payloadIdentity,
                            channelId = n.channelId, channelTransactionId = n.channelTransactionId,
                            money = n.amount.toContractMoney(n.currency), result = n.result, occurredAt = n.occurredAt,
                            firstReceivedAt = n.firstReceivedAt, lastReceivedAt = n.lastReceivedAt,
                            receiveCount = n.receiveCount, verified = n.verified, accepted = n.accepted,
                            decision = ReferenceContractStatusMapper.paymentDisposition(n.decision),
                            verdictSummary = n.verdictSummary,
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
