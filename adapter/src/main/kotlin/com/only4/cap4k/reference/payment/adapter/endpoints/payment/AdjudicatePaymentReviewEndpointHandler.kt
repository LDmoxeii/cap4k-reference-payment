package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.review.AdjudicatePaymentReviewCmd
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.AdjudicatePaymentReviewEndpoint
import org.springframework.stereotype.Component

@Component
class AdjudicatePaymentReviewEndpointHandler : EndpointHandler<AdjudicatePaymentReviewEndpoint.Request, AdjudicatePaymentReviewEndpoint.Response> {
    override fun handle(request: AdjudicatePaymentReviewEndpoint.Request): AdjudicatePaymentReviewEndpoint.Response {
        val response = Mediator.commands.send(
            AdjudicatePaymentReviewCmd.Request(
                paymentId = request.paymentId,
                reviewId = request.reviewId,
                decisionIdentity = request.decisionIdentity,
                decision = request.decision,
                operatorIdentity = request.operatorIdentity,
                operatorRole = request.operatorRole,
                authorizationMaterial = request.authorizationMaterial,
                reason = request.reason,
                evidence = request.evidence,
                decidedAt = request.decidedAt,
                eligibilityImpact = request.eligibilityImpact,
                remediationReference = request.remediationReference,
            )
        )
        return AdjudicatePaymentReviewEndpoint.Response(
            paymentStatus = response.paymentStatus,
            reviewStatus = response.reviewStatus,
            decisionCount = response.decisionCount,
            settlementEligible = response.settlementEligible,
            notificationIntentState = response.notificationIntentState,
        )
    }
}
