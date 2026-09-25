package com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationDeliveryOutcome
import java.time.LocalDateTime

data class MerchantNotificationDeliveryAttemptCreation(
    val attemptSequence: Int,
    val deliveryIdentity: String,
    val contentIdentity: String,
    val outcome: MerchantNotificationDeliveryOutcome,
    val diagnostic: String?,
    val recordedAt: LocalDateTime
)
