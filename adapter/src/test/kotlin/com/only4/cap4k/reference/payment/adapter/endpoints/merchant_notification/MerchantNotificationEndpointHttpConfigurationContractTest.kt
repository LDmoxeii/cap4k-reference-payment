package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_notification

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class MerchantNotificationEndpointHttpConfigurationContractTest {
    @Test
    fun `notification routes bind detail authoritative search and retry command`() {
        val binding = Files.readString(
            Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/merchant_notification/MerchantNotificationEndpointHttpConfiguration.kt"),
        )
        val readModel = Files.readString(
            Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/application/queries/merchant_notification/read/MerchantNotificationReadModel.kt"),
        )
        assertContains(binding, "path = \"/api/merchant-notifications/{notificationId}\"")
        assertContains(binding, "path = \"/api/merchant-notifications/search\"")
        assertContains(binding, "path = \"/api/merchant-notifications/{notificationId}/retries\"")
        assertContains(binding, "request.body(RetryMerchantNotificationEndpoint.Request::class).copy(")
        assertContains(readModel, "from merchant_notification n")
        assertContains(readModel, "order by n.created_at desc, n.id desc limit ?")
        assertContains(readModel, "from merchant_notification_delivery_attempt where merchant_notification_id = ?1")
    }

    @Test
    fun `sender fixture binds configure reset and clear independently`() {
        val binding = Files.readString(
            Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/reference_fixture/ReferenceFixtureHttpConfiguration.kt"),
        )
        assertContains(binding, "path = \"/api/reference-fixtures/merchant-notification-sender-script\"")
        assertContains(binding, "path = \"/api/reference-fixtures/merchant-notification-sender-script/reset\"")
        assertContains(binding, "path = \"/api/reference-fixtures/merchant-notification-sender-script/clear\"")
    }
}
