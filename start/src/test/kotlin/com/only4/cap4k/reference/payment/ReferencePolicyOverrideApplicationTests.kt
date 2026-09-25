package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/** PAY-AC-101: the public reference fixture must drive business outcomes, not just echo values. */
@SpringBootTest
@AutoConfigureMockMvc
class ReferencePolicyOverrideApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val objectMapper: ObjectMapper,
) {

    @AfterEach
    fun restoreReferenceDefaults() {
        postJson("/api/reference-fixtures/policy/reset", emptyMap<String, Any>(), 200)
        postJson("/api/reference-fixtures/clock/reset", emptyMap<String, Any>(), 200)
    }

    @Test
    fun `PAY-AC-101 temporal money retry and page overrides are observable business inputs`() {
        val marker = "policy-${sequence.incrementAndGet()}"
        val base = Instant.parse("2026-09-10T01:00:00Z")
        postJson("/api/reference-fixtures/clock/set", mapOf("instant" to base), 200)
        val configured = postJson(
            "/api/reference-fixtures/policy",
            mapOf(
                "paymentExpiry" to "PT10M",
                "unknownResultReviewAfter" to "PT2M",
                "operationPollRetryAfterMs" to 25,
                "operationObservationTimeout" to "PT5S",
                "refundWindow" to "P1D",
                "maxRefundAttempts" to 3,
                "billReadMaxAttempts" to 4,
                "billReadBackoff" to "PT10S",
                "feeRate" to "0.008",
                "roundingMode" to "DOWN",
                "currencyPrecisions" to mapOf("CNY" to 2),
                "enabledCurrencies" to listOf("CNY"),
                "merchantNotificationMaxAttempts" to 4,
                "defaultPageSize" to 1,
                "maxPageSize" to 2,
            ),
            200,
        )["policy"]
        assertThat(configured.requiredText("paymentExpiry")).isEqualTo("PT10M")
        assertThat(configured.requiredText("unknownResultReviewAfter")).isEqualTo("PT2M")
        assertThat(configured.requiredText("refundWindow")).isEqualTo("P1D")
        assertThat(configured["operationPollRetryAfterMs"].asInt()).isEqualTo(25)
        assertThat(configured["defaultPageSize"].asInt()).isEqualTo(1)

        val paymentId = createSucceededPayment(marker, base)
        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("expiresAt")).isEqualTo(base.plusSeconds(600).toString())
        assertThat(payment["feeSnapshot"].requiredText("feeRate")).isEqualTo("0.008")
        assertThat(payment["feeSnapshot"].requiredText("roundingMode")).isEqualTo("DOWN")
        assertThat(payment["feeSnapshot"]["currencyPrecision"].asInt()).isEqualTo(2)

        createPayment("$marker-page")
        val page = postJson("/api/payments/search", mapOf("merchantId" to "M-001"), 200)
        assertThat(page["pageSize"].asInt()).isEqualTo(1)
        assertThat(page["items"]).hasSize(1)
        assertThat(page.requiredText("nextCursor")).isNotBlank()

        val refundId = postJson(
            "/api/refunds",
            mapOf(
                "merchantId" to "M-001",
                "idempotencyKey" to "$marker-refund",
                "merchantRefundNo" to "$marker-refund-no",
                "paymentId" to paymentId,
                "money" to money("1000"),
                "reason" to "verify policy driven unknown review threshold",
            ),
            201,
        ).requiredText("refundId")
        val refundAttemptId = postJson(
            "/api/refunds/$refundId/attempts",
            mapOf("idempotencyKey" to "$marker-refund-attempt"),
            201,
        ).requiredText("refundAttemptId")
        val submitted = postJson(
            "/api/refunds/$refundId/attempts/$refundAttemptId/submissions",
            mapOf("idempotencyKey" to "$marker-refund-submit"),
            200,
        )
        val channelRefundId = "fake-refund-${submitted.requiredText("requestIdentity")}"
        val refundNotification = "$marker-refund-unknown"
        val refundPayload = registerEvidence(
            kind = "REFUND",
            externalIdentity = refundNotification,
            associationIdentity = "$refundId|$refundAttemptId|$channelRefundId",
            amountMinor = "1000",
        )
        postJson(
            "/api/channel/refund-results",
            mapOf(
                "channelId" to "C-001",
                "notificationId" to refundNotification,
                "refundId" to refundId,
                "refundAttemptId" to refundAttemptId,
                "channelRefundId" to channelRefundId,
                "money" to money("1000"),
                "result" to "UNKNOWN",
                "occurredAt" to base,
                "rawPayload" to refundPayload,
            ),
            200,
        )

        postJson("/api/reference-fixtures/clock/advance", mapOf("duration" to "PT1M"), 200)
        val early = postJson(
            "/api/reference-fixtures/maintenance",
            mapOf("action" to "REFUND_UNKNOWN_REVIEW"),
            200,
        )
        assertThat(early["changedCount"].asInt()).isZero()
        postJson("/api/reference-fixtures/clock/advance", mapOf("duration" to "PT1M"), 200)
        val due = postJson(
            "/api/reference-fixtures/maintenance",
            mapOf("action" to "REFUND_UNKNOWN_REVIEW"),
            200,
        )
        assertThat(due["changedCount"].asInt()).isEqualTo(1)
        assertThat(getJson("/api/refunds/$refundId").requiredText("finality")).isEqualTo("REVIEW_REQUIRED")

        postJson("/api/reference-fixtures/clock/set", mapOf("instant" to base.plusSeconds(2 * 86_400)), 200)
        val expiredRefund = postJsonResult(
            "/api/refunds",
            mapOf(
                "merchantId" to "M-001",
                "idempotencyKey" to "$marker-expired-refund",
                "merchantRefundNo" to "$marker-expired-refund-no",
                "paymentId" to paymentId,
                "money" to money("100"),
                "reason" to "outside the explicit P1D refund window",
            ),
        )
        assertThat(expiredRefund.status).isEqualTo(400)
        assertThat(expiredRefund.body.requiredText("code")).isEqualTo("VALIDATION_ERROR")
    }

    private fun createSucceededPayment(marker: String, occurredAt: Instant): String {
        val paymentId = createPayment(marker).requiredText("paymentId")
        assertThat(getJson("/api/payments/$paymentId").requiredText("expiresAt"))
            .isEqualTo(occurredAt.plusSeconds(600).toString())
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            mapOf("idempotencyKey" to "$marker-attempt"),
            201,
        ).requiredText("paymentAttemptId")
        postJson(
            "/api/payments/$paymentId/attempts/$attemptId/submissions",
            mapOf("idempotencyKey" to "$marker-submit"),
            200,
        )
        val notificationId = "$marker-payment-success"
        val transactionId = "$marker-channel-transaction"
        val payload = registerEvidence(
            kind = "PAYMENT",
            externalIdentity = notificationId,
            associationIdentity = "$paymentId|$attemptId|$transactionId",
            amountMinor = "10000",
        )
        postJson(
            "/api/channel/payment-results",
            mapOf(
                "channelId" to "C-001",
                "notificationId" to notificationId,
                "paymentId" to paymentId,
                "paymentAttemptId" to attemptId,
                "channelTransactionId" to transactionId,
                "money" to money("10000"),
                "result" to "SUCCESS",
                "occurredAt" to occurredAt,
                "rawPayload" to payload,
            ),
            200,
        )
        return paymentId
    }

    private fun createPayment(marker: String): JsonNode = postJson(
        "/api/payments",
        mapOf(
            "merchantId" to "M-001",
            "merchantOrderNumber" to "$marker-order",
            "idempotencyKey" to "$marker-payment",
            "money" to money("10000"),
            "paymentMethod" to "CARD",
        ),
        201,
    )

    private fun registerEvidence(
        kind: String,
        externalIdentity: String,
        associationIdentity: String,
        amountMinor: String,
    ): String {
        val rawPayload = "reference-policy-${kind.lowercase()}-$externalIdentity"
        postJson(
            "/api/reference-fixtures/callback-evidence",
            mapOf(
                "idempotencyKey" to "evidence-$externalIdentity",
                "kind" to kind,
                "channelId" to "C-001",
                "externalIdentity" to externalIdentity,
                "associationIdentity" to associationIdentity,
                "money" to money(amountMinor),
                "rawPayload" to rawPayload,
            ),
            201,
        )
        return rawPayload
    }

    private fun money(amountMinor: String): Map<String, String> = mapOf(
        "currency" to "CNY",
        "amountMinor" to amountMinor,
    )

    private fun postJson(path: String, payload: Any, expectedStatus: Int): JsonNode {
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)),
        ).andReturn()
        assertThat(result.response.status)
            .withFailMessage("POST %s expected %s but got %s: %s", path, expectedStatus, result.response.status, result.response.contentAsString)
            .isEqualTo(expectedStatus)
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun postJsonResult(path: String, payload: Any): HttpResult {
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)),
        ).andReturn()
        return HttpResult(result.response.status, objectMapper.readTree(result.response.contentAsByteArray))
    }

    private fun getJson(path: String): JsonNode {
        val result = mockMvc.perform(get(path)).andReturn()
        assertThat(result.response.status)
            .withFailMessage("GET %s expected 200 but got %s: %s", path, result.response.status, result.response.contentAsString)
            .isEqualTo(200)
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun JsonNode.requiredText(field: String): String =
        requireNotNull(get(field)) { "missing JSON field $field in $this" }.asText()

    private data class HttpResult(val status: Int, val body: JsonNode)

    private companion object {
        val sequence = AtomicInteger()
    }
}
