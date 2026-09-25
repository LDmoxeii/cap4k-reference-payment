package com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationDeliveryOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.factory.MerchantNotificationFactory
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MerchantNotificationBehaviorTest {
    @Test
    fun `failure followed by success preserves intent and every delivery attempt`() {
        val notification = notification()
        val firstIdentity = notification.nextDeliveryIdentity()
        val first = notification.recordDelivery(firstIdentity, MerchantNotificationDeliveryOutcome.FAILURE, "sender unavailable", NOW)

        assertThat(notification.status).isEqualTo(MerchantNotificationStatus.FAILED)
        assertThat(notification.finality).isEqualTo("NON_FINAL")
        assertThat(first.attemptSequence).isEqualTo(1)
        assertThat(first.contentIdentity).isEqualTo("content-sha256-1")
        assertThat(first.recordedAt).isEqualTo(NOW)

        val secondIdentity = notification.nextDeliveryIdentity()
        assertThat(secondIdentity).isNotEqualTo(firstIdentity)
        val second = notification.recordDelivery(secondIdentity, MerchantNotificationDeliveryOutcome.SUCCESS, null, NOW.plusMinutes(1))

        assertThat(second.attemptSequence).isEqualTo(2)
        assertThat(notification.status).isEqualTo(MerchantNotificationStatus.DELIVERED)
        assertThat(notification.finality).isEqualTo("FINAL")
        assertThat(notification.deliveryAttempts.map { it.deliveryIdentity })
            .containsExactly(firstIdentity, secondIdentity)
        assertThat(notification.notificationIdentity).isEqualTo("payment:success:1")
        assertThat(notification.contentIdentity).isEqualTo("content-sha256-1")
        assertThat(notification.sourceFactIdentity).isEqualTo("payment-success:1")
        assertThat(notification.content).isEqualTo("{\"event\":\"PAYMENT_SUCCEEDED\"}")
        assertThatThrownBy { notification.nextDeliveryIdentity() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("NOTIFICATION_DELIVERY_NOT_RETRYABLE")
    }

    @Test
    fun `same delivery identity replays observation while conflicting outcome is rejected`() {
        val notification = notification()
        val identity = notification.nextDeliveryIdentity()
        val first = notification.recordDelivery(identity, "FAILURE", "temporary failure", NOW)
        val replay = notification.recordDelivery(identity, "FAILURE", "temporary failure", NOW.plusHours(1))

        assertThat(replay).isSameAs(first)
        assertThat(notification.deliveryAttempts).hasSize(1)
        assertThat(first.recordedAt).isEqualTo(NOW)
        assertThatThrownBy {
            notification.recordDelivery(identity, "SUCCESS", "temporary failure", NOW.plusHours(1))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("NOTIFICATION_DELIVERY_IDENTITY_CONFLICT")
        assertThat(notification.status).isEqualTo(MerchantNotificationStatus.FAILED)
        assertThat(notification.deliveryAttempts).hasSize(1)
    }

    @Test
    fun `unknown result is observable and prevents another send`() {
        val notification = notification()
        notification.recordDelivery(notification.nextDeliveryIdentity(), "RESULT_UNKNOWN", "timeout after submit", NOW)

        assertThat(notification.status).isEqualTo(MerchantNotificationStatus.RESULT_UNKNOWN)
        assertThat(notification.finality).isEqualTo("REVIEW_REQUIRED")
        assertThatThrownBy { notification.nextDeliveryIdentity() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("NOTIFICATION_DELIVERY_NOT_RETRYABLE")
        assertThat(notification.deliveryAttempts).hasSize(1)
    }

    @Test
    fun `frozen retry limit becomes final on last explicit failure`() {
        val notification = notification(maxAttempts = 2)
        notification.recordDelivery(notification.nextDeliveryIdentity(), "FAILURE", null, NOW)
        notification.recordDelivery(notification.nextDeliveryIdentity(), "FAILURE", null, NOW.plusMinutes(1))

        assertThat(notification.status).isEqualTo(MerchantNotificationStatus.FAILED)
        assertThat(notification.finality).isEqualTo("FINAL")
        assertThatThrownBy { notification.nextDeliveryIdentity() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("NOTIFICATION_ATTEMPTS_EXHAUSTED")
        assertThat(notification.deliveryAttempts.map { it.attemptSequence })
            .containsExactly(1, 2)
    }

    private fun notification(maxAttempts: Int = 3): MerchantNotification =
        MerchantNotificationFactory().create(
            MerchantNotificationFactory.Payload(
                notificationIdentity = "payment:success:1",
                contentIdentity = "content-sha256-1",
                merchantId = "M-001",
                sourceKind = "PAYMENT",
                sourceFactIdentity = "payment-success:1",
                paymentId = null,
                content = "{\"event\":\"PAYMENT_SUCCEEDED\"}",
                maxAttempts = maxAttempts,
            )
        )

    companion object {
        private val NOW = LocalDateTime.parse("2026-09-22T09:00:00")
    }
}
