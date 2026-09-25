package com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationDeliveryOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime

fun MerchantNotification.onCreate() = Unit
fun MerchantNotification.onDeleted() = Unit

/**
 * Append one delivery observation to this frozen notification intent. A failed send may be retried
 * with the same notification/content identities; a successful or unknown send cannot be sent again.
 * The sender's outcome has no transition on the source payment, refund, run, or settlement.
 */
fun MerchantNotification.recordDelivery(
    deliveryIdentity: String,
    outcome: MerchantNotificationDeliveryOutcome,
    diagnostic: String?,
    recordedAt: LocalDateTime,
): MerchantNotificationDeliveryAttempt {
    require(deliveryIdentity.isNotBlank()) { "投递身份不能为空" }
    require(deliveryIdentity.length <= 256) { "投递身份过长" }
    require(diagnostic == null || diagnostic.length <= 2048) { "投递诊断过长" }

    deliveryAttempts.firstOrNull { it.deliveryIdentity == deliveryIdentity }?.let { existing ->
        require(existing.contentIdentity == contentIdentity && existing.outcome == outcome && existing.diagnostic == diagnostic) {
            "NOTIFICATION_DELIVERY_IDENTITY_CONFLICT"
        }
        return existing
    }
    require(status == MerchantNotificationStatus.PENDING || status == MerchantNotificationStatus.FAILED) {
        "NOTIFICATION_DELIVERY_NOT_RETRYABLE"
    }
    require(deliveryAttempts.size < maxAttempts) { "NOTIFICATION_ATTEMPTS_EXHAUSTED" }

    val attempt = MerchantNotificationDeliveryAttempt(
        attemptSequence = deliveryAttempts.size + 1,
        deliveryIdentity = deliveryIdentity,
        contentIdentity = contentIdentity,
        outcome = outcome,
        diagnostic = diagnostic,
        recordedAt = recordedAt,
    )
    deliveryAttempts.add(attempt)
    when (outcome) {
        MerchantNotificationDeliveryOutcome.SUCCESS -> {
            status = MerchantNotificationStatus.DELIVERED
            finality = "FINAL"
        }
        MerchantNotificationDeliveryOutcome.FAILURE -> {
            status = MerchantNotificationStatus.FAILED
            finality = if (deliveryAttempts.size >= maxAttempts) "FINAL" else "NON_FINAL"
        }
        MerchantNotificationDeliveryOutcome.RESULT_UNKNOWN -> {
            status = MerchantNotificationStatus.RESULT_UNKNOWN
            finality = "REVIEW_REQUIRED"
        }
    }
    return attempt
}

/** Stable identity for the next sender request; replaying the same sequence never creates a new send identity. */
fun MerchantNotification.nextDeliveryIdentity(): String {
    require(status == MerchantNotificationStatus.PENDING || status == MerchantNotificationStatus.FAILED) {
        "NOTIFICATION_DELIVERY_NOT_RETRYABLE"
    }
    require(deliveryAttempts.size < maxAttempts) { "NOTIFICATION_ATTEMPTS_EXHAUSTED" }
    val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(notificationIdentity.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "merchant-notification:$fingerprint:${deliveryAttempts.size + 1}"
}

fun MerchantNotification.recordDelivery(
    deliveryIdentity: String,
    outcome: String,
    diagnostic: String?,
    recordedAt: LocalDateTime,
): MerchantNotificationDeliveryAttempt =
    recordDelivery(deliveryIdentity, MerchantNotificationDeliveryOutcome.valueOf(outcome.trim().uppercase()), diagnostic, recordedAt)
