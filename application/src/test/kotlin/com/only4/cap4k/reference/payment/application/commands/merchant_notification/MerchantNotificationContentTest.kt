package com.only4.cap4k.reference.payment.application.commands.merchant_notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MerchantNotificationContentTest {
    @Test
    fun `canonical content is independent of insertion order and escapes payload values`() {
        val first = MerchantNotificationService.canonicalContent(
            linkedMapOf("status" to "SUCCEEDED", "message" to "paid \"now\"\n"),
        )
        val second = MerchantNotificationService.canonicalContent(
            linkedMapOf("message" to "paid \"now\"\n", "status" to "SUCCEEDED"),
        )
        assertEquals("{\"message\":\"paid \\\"now\\\"\\n\",\"status\":\"SUCCEEDED\"}", first)
        assertEquals(first, second)
        assertEquals(
            MerchantNotificationService.contentIdentity(first),
            MerchantNotificationService.contentIdentity(second),
        )
        assertNotEquals(
            MerchantNotificationService.contentIdentity(first),
            MerchantNotificationService.contentIdentity(
                MerchantNotificationService.canonicalContent(mapOf("status" to "FAILED")),
            ),
        )
        assertEquals(
            "merchant-notification:PAYMENT:payment-success:1",
            MerchantNotificationService.notificationIdentity(" payment ", " payment-success:1 "),
        )
    }
}
