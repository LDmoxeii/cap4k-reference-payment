package com.only4.cap4k.reference.payment.domain.aggregates.payment

import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewSettlementImpact
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewType
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import java.time.LocalDateTime

data class PaymentReviewCaseCreation(
    val reviewIdentity: String,
    val type: PaymentReviewType,
    val status: PaymentReviewStatus,
    val openedAt: LocalDateTime,
    val triggeringPaymentStatus: PaymentStatus,
    val triggeringAttemptIdentities: String?,
    val triggeringReceiptIdentities: String?,
    val summary: String,
    val settlementImpact: PaymentReviewSettlementImpact,
    val resolvedAt: LocalDateTime?,
    val paymentReviewDecisions: List<PaymentReviewDecisionCreation> = emptyList()
)
