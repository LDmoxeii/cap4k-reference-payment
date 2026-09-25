package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceMerchantNotificationSenderRegistry
import com.only4.cap4k.reference.payment.application.commands.merchant_notification.MerchantNotificationService
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@SpringBootTest
@AutoConfigureMockMvc
class MerchantNotificationReferenceApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val mapper: ObjectMapper,
    @param:Autowired private val jdbc: JdbcTemplate,
    @param:Autowired private val sender: ReferenceMerchantNotificationSenderRegistry,
) {
    @Test
    fun `failed delivery retries once per accepted operation and preserves frozen content`() {
        val marker = UUID.randomUUID().toString()
        val source = "payment-success:$marker"
        configure(mapOf("sourceKind" to "PAYMENT", "sourceFactIdentity" to source, "script" to "FAILURE"))
        val id = create("M-$marker", source)
        val original = detail(id)
        assertThat(original["status"].asText()).isEqualTo("FAILED")
        assertThat(original["deliveryAttempts"]).hasSize(1)
        assertThat(original["deliveryAttempts"][0]["outcome"].asText()).isEqualTo("FAILURE")
        val frozenContent = original["content"].asText()
        val fingerprint = original["contentIdentity"].asText()
        configure(mapOf("notificationIdentity" to original["notificationIdentity"].asText(), "script" to "SUCCESS"))

        val key = "notification-retry-$marker"
        val first = post("/api/merchant-notifications/$id/retries", mapOf("idempotencyKey" to key))
        assertThat(first.status).isEqualTo(200)
        assertThat(first.body["receipt"]["acceptanceStatus"].asText()).isEqualTo("ACCEPTED")
        val replay = post("/api/merchant-notifications/$id/retries", mapOf("idempotencyKey" to key))
        assertThat(replay.status).isEqualTo(200)
        assertThat(replay.body["receipt"]["acceptanceStatus"].asText()).isEqualTo("ALREADY_ACCEPTED")
        assertThat(replay.body["receipt"]["operationId"].asText())
            .isEqualTo(first.body["receipt"]["operationId"].asText())
        val after = detail(id)
        assertThat(after["status"].asText()).isEqualTo("DELIVERED")
        assertThat(after["finality"].asText()).isEqualTo("FINAL")
        assertThat(after["content"].asText()).isEqualTo(frozenContent)
        assertThat(after["contentIdentity"].asText()).isEqualTo(fingerprint)
        assertThat(after["deliveryAttempts"]).hasSize(2)
        assertThat(after["deliveryAttempts"][1]["outcome"].asText()).isEqualTo("SUCCESS")
        assertThat(after["deliveryAttempts"][0]["contentIdentity"].asText()).isEqualTo(fingerprint)
        assertThat(after["deliveryAttempts"][1]["contentIdentity"].asText()).isEqualTo(fingerprint)
        assertThat(attemptCount(id)).isEqualTo(2)
        assertThat(sender.observedDeliveryCount()).isGreaterThanOrEqualTo(2)

        val anotherSource = "payment-success:${UUID.randomUUID()}"
        configure(mapOf("sourceKind" to "PAYMENT", "sourceFactIdentity" to anotherSource, "script" to "FAILURE"))
        val anotherId = create("M-$marker", anotherSource)
        val collision = post("/api/merchant-notifications/$anotherId/retries", mapOf("idempotencyKey" to key))
        assertThat(collision.status).isEqualTo(409)
        assertThat(collision.body["code"].asText()).isEqualTo("IDEMPOTENCY_CONFLICT")
        assertThat(attemptCount(anotherId)).isEqualTo(1)
    }

    @Test
    fun `unknown result cannot be retried and keyset search rejects another filter`() {
        val marker = UUID.randomUUID().toString()
        val merchant = "M-$marker"
        val firstSource = "settlement-success:$marker:1"
        configure(mapOf("sourceKind" to "SETTLEMENT", "sourceFactIdentity" to firstSource, "script" to "RESULT_UNKNOWN"))
        val unknownId = create(merchant, firstSource, "SETTLEMENT")
        val rejected = post(
            "/api/merchant-notifications/$unknownId/retries",
            mapOf("idempotencyKey" to "retry-unknown-$marker"),
        )
        assertThat(rejected.status).isEqualTo(409)
        assertThat(rejected.body["code"].asText()).isEqualTo("NOTIFICATION_DELIVERY_NOT_RETRYABLE")
        assertThat(attemptCount(unknownId)).isEqualTo(1)
        assertThat(operationCount("retry-unknown-$marker")).isZero()

        val secondSource = "settlement-success:$marker:2"
        val secondId = create(merchant, secondSource, "SETTLEMENT")
        val page = post(
            "/api/merchant-notifications/search",
            mapOf("merchantId" to merchant, "sourceKind" to "SETTLEMENT", "pageSize" to 1),
        )
        assertThat(page.status).isEqualTo(200)
        assertThat(page.body["items"]).hasSize(1)
        val cursor = page.body["nextCursor"].asText()
        assertThat(cursor).isNotBlank()
        val next = post(
            "/api/merchant-notifications/search",
            mapOf("merchantId" to merchant, "sourceKind" to "SETTLEMENT", "pageSize" to 1, "cursor" to cursor),
        )
        assertThat(next.status).isEqualTo(200)
        assertThat(next.body["items"]).hasSize(1)
        assertThat(setOf(page.body["items"][0]["notificationId"].asText(), next.body["items"][0]["notificationId"].asText()))
            .containsExactlyInAnyOrder(unknownId, secondId)
        val otherFilter = post(
            "/api/merchant-notifications/search",
            mapOf("merchantId" to merchant, "status" to "FAILED", "pageSize" to 1, "cursor" to cursor),
        )
        assertThat(otherFilter.status).isEqualTo(400)
        assertThat(otherFilter.body["code"].asText()).isEqualTo("INVALID_CURSOR")
    }

    private fun configure(body: Map<String, String>) {
        val response = post("/api/reference-fixtures/merchant-notification-sender-script", body)
        assertThat(response.status).isEqualTo(200)
    }

    private fun create(merchant: String, source: String, kind: String = "PAYMENT"): String =
        Mediator.commands.send(
            CreateNotificationForHttpTestCmd.Request(
                merchantId = merchant,
                sourceKind = kind,
                sourceFactIdentity = source,
            ),
        )

    private fun detail(id: String): JsonNode {
        val result = mockMvc.perform(get("/api/merchant-notifications/$id")).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        return mapper.readTree(result.response.contentAsByteArray)["notification"]
    }

    private fun attemptCount(id: String): Int = jdbc.queryForObject(
        "select count(*) from merchant_notification_delivery_attempt where merchant_notification_id = ?",
        Int::class.java, id,
    ) ?: -1

    private fun operationCount(key: String): Int = jdbc.queryForObject(
        "select count(*) from operation where command_type = 'RetryMerchantNotification' and idempotency_key = ?",
        Int::class.java, key,
    ) ?: -1

    private fun post(path: String, body: Any): HttpResponse {
        val result = mockMvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)),
        ).andReturn()
        return HttpResponse(result.response.status, mapper.readTree(result.response.contentAsByteArray))
    }

    private data class HttpResponse(val status: Int, val body: JsonNode)
}

internal object CreateNotificationForHttpTestCmd {
    @Service
    class Handler(private val notifications: MerchantNotificationService) : CommandHandler<Request, String> {
        override fun handle(command: Request): String = notifications.createAndDeliver(
            MerchantNotificationService.Intent(
                merchantId = command.merchantId,
                sourceKind = command.sourceKind,
                sourceFactIdentity = command.sourceFactIdentity,
                paymentId = null,
                content = mapOf("source" to command.sourceFactIdentity, "status" to "SUCCEEDED"),
            ),
        ).notification.id.toString()
    }

    data class Request(
        val merchantId: String,
        val sourceKind: String,
        val sourceFactIdentity: String,
    ) : Command<String>
}
