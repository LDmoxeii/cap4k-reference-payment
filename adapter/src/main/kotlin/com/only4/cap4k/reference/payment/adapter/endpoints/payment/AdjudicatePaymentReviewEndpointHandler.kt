package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.review.AdjudicatePaymentReviewCmd
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.AdjudicatePaymentReviewEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class AdjudicatePaymentReviewEndpointHandler(
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<AdjudicatePaymentReviewEndpoint.Request, AdjudicatePaymentReviewEndpoint.Response> {
    override fun handle(request: AdjudicatePaymentReviewEndpoint.Request): AdjudicatePaymentReviewEndpoint.Response {
        val actor = actorContextResolver.consume()
        val response = Mediator.commands.send(
            AdjudicatePaymentReviewCmd.Request(
                paymentId = PaymentId.parse(request.paymentId),
                merchantId = request.merchantId,
                idempotencyKey = request.idempotencyKey,
                reviewId = request.reviewId,
                decisionIdentity = request.decisionIdentity,
                decision = request.decision,
                operatorIdentity = actor.actorId,
                operatorRole = actor.role,
                reason = request.reason,
                evidence = request.evidence,
                decidedAt = Instant.now(clock),
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
            decisionId = response.decisionId,
            decisionIdentity = response.decisionIdentity,
            decision = response.decision,
            actorId = response.actorId,
            reason = response.reason,
            evidence = response.evidence,
            decidedAt = response.decidedAt,
            receipt = response.receipt,
        )
    }
}
