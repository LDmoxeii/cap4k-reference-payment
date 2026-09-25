package com.only4.cap4k.reference.payment.domain.aggregates.payment

import java.time.LocalDateTime

/** Rehydrates the immutable business fields of one persisted payment channel submission receipt. */
data class PaymentSubmissionReceiptCreation(
    val submissionIdentity: String,
    val requestIdentity: String,
    val channelId: String,
    val submittedAt: LocalDateTime,
    val outcome: String,
    val channelReference: String?,
    val diagnosticSummary: String?,
)
