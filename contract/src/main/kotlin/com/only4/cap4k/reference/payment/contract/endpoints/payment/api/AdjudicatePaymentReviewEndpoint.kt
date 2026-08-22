package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/** POST /api/payments/{paymentId}/reviews/{reviewId}/decisions */
@DesignBlockMetadata(tag = "endpoint", name = "AdjudicatePaymentReviewEndpoint", packageName = "payment.api", description = "POST /api/payments/{paymentId}/reviews/{reviewId}/decisions", aggregates = [], operationName = "payment.review.adjudicate", family = "endpoint")
object AdjudicatePaymentReviewEndpoint {
    const val OPERATION_NAME = "payment.review.adjudicate"
    data class Request(
        val paymentId: String,
        val reviewId: String,
        val decisionIdentity: String,
        val decision: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val authorizationMaterial: String,
        val reason: String,
        val evidence: String,
        val decidedAt: Instant,
        val eligibilityImpact: String,
        val remediationReference: String?,
    ) : EndpointRequest<Response>
    data class Response(
        val paymentStatus: String,
        val reviewStatus: String,
        val decisionCount: Int,
        val settlementEligible: Boolean,
        val notificationIntentState: String?,
    )
}
