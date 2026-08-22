package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionFinalResult
import java.math.BigDecimal
import java.time.LocalDateTime

data class SettlementExecutionAttemptCreation(
    val attemptSequence: Int,
    val executionGroupIdentity: String,
    val requestIdentity: String,
    val channelId: String,
    val status: SettlementExecutionAttemptStatus,
    val initiatedAt: LocalDateTime,
    val acceptedAt: LocalDateTime?,
    val reviewAfterMinutesSnapshot: Int,
    val reviewAfterAt: LocalDateTime,
    val amount: BigDecimal,
    val currency: String,
    val externalSettlementIdentity: String?,
    val finalResult: SettlementExecutionFinalResult?,
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
    val settlementResultReceipts: List<SettlementResultReceiptCreation> = emptyList()
)
