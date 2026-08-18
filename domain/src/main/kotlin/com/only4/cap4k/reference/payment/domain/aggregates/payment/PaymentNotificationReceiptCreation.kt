package com.only4.cap4k.reference.payment.domain.aggregates.payment

import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentNotificationReceiptCreation(
    val notificationIdentity: String,
    val channelId: String,
    val channelTransactionId: String,
    val amount: BigDecimal,
    val currency: String,
    val result: String,
    val occurredAt: LocalDateTime,
    val firstReceivedAt: LocalDateTime,
    val lastReceivedAt: LocalDateTime,
    val receiveCount: Int = 1,
    val verified: Boolean = false,
    val accepted: Boolean = false,
    val decision: String,
    val verdictSummary: String?,
    val rejectionSummary: String?,
    val conflictSummary: String?
)
