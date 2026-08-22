package com.only4.cap4k.reference.payment.domain.aggregates.payment

import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import java.time.LocalDateTime

data class PaymentAttemptCreation(
    val channelId: String, val channelConfigurationId: String, val channelConfigurationSnapshot: String,
    val requestIdentity: String, val status: PaymentAttemptStatus, val initiatedAt: LocalDateTime,
    val channelTransactionId: String?, val finalResult: PaymentAttemptFinalResult?, val resultOccurredAt: LocalDateTime?,
    val notificationIdentity: String?, val notificationReceiveCount: Int = 0,
    val notificationFirstReceivedAt: LocalDateTime?, val notificationLastReceivedAt: LocalDateTime?,
    val verifiedNotificationCount: Int = 0, val rejectedNotificationCount: Int = 0,
    val conflictingNotificationCount: Int = 0, val verdictSummary: String?, val rejectionSummary: String?,
    val conflictSummary: String?, val paymentNotificationReceipts: List<PaymentNotificationReceiptCreation> = emptyList(),
)
