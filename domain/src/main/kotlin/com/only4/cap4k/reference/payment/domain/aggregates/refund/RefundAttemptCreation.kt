package com.only4.cap4k.reference.payment.domain.aggregates.refund

import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import java.time.LocalDateTime

data class RefundAttemptCreation(
    val channelId: String,
    val channelConfigurationId: String,
    val channelConfigurationSnapshot: String,
    val requestIdentity: String,
    val status: RefundAttemptStatus,
    val initiatedAt: LocalDateTime,
    val acceptedAt: LocalDateTime?,
    val reviewAfterAt: LocalDateTime,
    val channelRefundId: String?,
    val finalResult: RefundAttemptFinalResult?,
    val resultOccurredAt: LocalDateTime?,
    val notificationReceiveCount: Int = 0,
    val notificationFirstReceivedAt: LocalDateTime?,
    val notificationLastReceivedAt: LocalDateTime?,
    val verifiedNotificationCount: Int = 0,
    val rejectedNotificationCount: Int = 0,
    val conflictingNotificationCount: Int = 0,
    val verdictSummary: String?,
    val rejectionSummary: String?,
    val conflictSummary: String?,
    val refundNotificationReceipts: List<RefundNotificationReceiptCreation> = emptyList()
)
