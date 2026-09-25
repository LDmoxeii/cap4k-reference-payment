package com.only4.cap4k.reference.payment.adapter.reference

import com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_notification.sender.SendMerchantNotificationHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_notification.sender.SendMerchantNotification
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotificationId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationDeliveryOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReferenceMerchantNotificationSenderRegistryTest {
    private val registry = ReferenceMerchantNotificationSenderRegistry()
    private val sender = SendMerchantNotificationHandler(registry)

    @Test
    fun `failed delivery replays its original observation while a new identity can succeed`() {
        registry.configureForSource("PAYMENT", "payment-success:1", ReferenceMerchantNotificationSenderRegistry.Script.FAILURE)
        val first = request("delivery-1")
        assertEquals(MerchantNotificationDeliveryOutcome.FAILURE, sender.call(first).outcome)
        registry.configureForNotification(
            first.notificationIdentity,
            ReferenceMerchantNotificationSenderRegistry.Script.SUCCESS,
        )
        assertEquals(MerchantNotificationDeliveryOutcome.FAILURE, sender.call(first).outcome)
        assertEquals(MerchantNotificationDeliveryOutcome.SUCCESS, sender.call(request("delivery-2")).outcome)
        assertEquals(2, registry.observedDeliveryCount())

        registry.resetForNotification(first.notificationIdentity)
        assertEquals(ReferenceMerchantNotificationSenderRegistry.Script.FAILURE,
            registry.scriptFor(first.notificationIdentity, first.sourceKind, first.sourceFactIdentity))
        registry.resetForSource(first.sourceKind, first.sourceFactIdentity)
        assertEquals(ReferenceMerchantNotificationSenderRegistry.DEFAULT,
            registry.scriptFor(first.notificationIdentity, first.sourceKind, first.sourceFactIdentity))
    }

    @Test
    fun `same delivery identity with altered frozen content is rejected`() {
        val first = request("delivery-1")
        sender.call(first)
        assertFailsWith<IllegalArgumentException> {
            sender.call(first.copy(contentIdentity = "another-content-identity"))
        }
        assertEquals(1, registry.observedDeliveryCount())
    }

    @Test
    fun `unknown sender result remains stable when script changes`() {
        val request = request("delivery-unknown")
        registry.configureForNotification(
            request.notificationIdentity,
            ReferenceMerchantNotificationSenderRegistry.Script.RESULT_UNKNOWN,
        )
        assertEquals(MerchantNotificationDeliveryOutcome.RESULT_UNKNOWN, sender.call(request).outcome)
        registry.configureForNotification(
            request.notificationIdentity,
            ReferenceMerchantNotificationSenderRegistry.Script.SUCCESS,
        )
        assertEquals(MerchantNotificationDeliveryOutcome.RESULT_UNKNOWN, sender.call(request).outcome)
        assertEquals(1, registry.observedDeliveryCount())
    }

    private fun request(deliveryIdentity: String) = SendMerchantNotification.Request(
        notificationId = MerchantNotificationId.parse("018f22a0-0000-7000-8000-000000000101"),
        notificationIdentity = "merchant-notification:PAYMENT:payment-success:1",
        contentIdentity = "content-sha256-1",
        deliveryIdentity = deliveryIdentity,
        merchantId = "M-001",
        sourceKind = "PAYMENT",
        sourceFactIdentity = "payment-success:1",
        content = "{\"status\":\"SUCCEEDED\"}",
    )
}
