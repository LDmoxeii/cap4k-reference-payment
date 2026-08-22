package com.only4.cap4k.reference.payment.domain.aggregates.payment

import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewDecisionType
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewEligibilityImpact
import java.time.LocalDateTime

data class PaymentReviewDecisionCreation(
    val decisionIdentity: String,
    val decision: PaymentReviewDecisionType,
    val operatorIdentity: String,
    val operatorRole: String,
    val authorizationOutcome: Boolean,
    val reason: String,
    val evidence: String,
    val decidedAt: LocalDateTime,
    val eligibilityImpact: PaymentReviewEligibilityImpact,
    val remediationReference: String?
)
