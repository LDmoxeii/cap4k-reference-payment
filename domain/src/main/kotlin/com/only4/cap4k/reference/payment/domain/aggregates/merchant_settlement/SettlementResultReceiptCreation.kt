package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementResultDisposition
import java.math.BigDecimal
import java.time.LocalDateTime

data class SettlementResultReceiptCreation(
    val notificationIdentity: String,
    val payloadFingerprint: String,
    val channelId: String,
    val executionGroupIdentity: String,
    val requestIdentity: String,
    val externalSettlementIdentity: String,
    val amount: BigDecimal,
    val currency: String,
    val result: String,
    val resultCode: String?,
    val occurredAt: LocalDateTime,
    val firstReceivedAt: LocalDateTime,
    val lastReceivedAt: LocalDateTime,
    val receiveCount: Int = 1,
    val verified: Boolean = false,
    val accepted: Boolean = false,
    val decision: SettlementResultDisposition,
    val verdictSummary: String?,
    val rejectionSummary: String?,
    val conflictSummary: String?
)
