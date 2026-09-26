package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceLogicalClock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get as mvcGet
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as mvcPost

@SpringBootTest
@AutoConfigureMockMvc
class CloseExpiredPaymentApplicationTests(
    @param:Autowired private val mvc: MockMvc,
    @param:Autowired private val json: ObjectMapper,
    @param:Autowired private val clock: ReferenceLogicalClock,
    @param:Autowired private val jdbc: JdbcTemplate,
) {
    @AfterEach
    fun resetClock() {
        clock.reset()
    }

    @Test
    fun `one expired payment closes with durable receipt and same key replays without a second effect`() {
        clock.set(Instant.parse("2026-09-25T00:00:00Z"))
        val paymentId = createPayment()
        val secondPaymentId = createPayment()
        val key = "close-${UUID.randomUUID()}"
        val body = mapOf("merchantId" to "M-001", "idempotencyKey" to key)
        val path = "/api/payments/$paymentId/close-expired"

        val tooEarly = post(path, body)
        assertThat(tooEarly.status).isEqualTo(409)
        assertThat(tooEarly.body["code"].asText()).isEqualTo("PAYMENT_NOT_EXPIRED")
        assertThat(operationCount(key)).isZero()

        clock.advance(Duration.ofMinutes(31))
        val accepted = post(path, body)
        assertThat(accepted.status).withFailMessage(accepted.body.toString()).isEqualTo(200)
        assertThat(accepted.body["paymentStatus"].asText()).isEqualTo("CLOSED")
        val receipt = accepted.body["receipt"]
        assertThat(receipt["commandType"].asText()).isEqualTo("CloseExpiredPayment")
        assertThat(receipt["resource"]["resourceId"].asText()).isEqualTo(paymentId)
        assertThat(receipt["readAfter"]["mode"].asText()).isEqualTo("READ_ONCE")
        assertThat(receipt["readAfter"]["resourceUrl"].asText()).isEqualTo("/api/payments/$paymentId")
        assertThat(receipt["acceptanceStatus"].asText()).isEqualTo("ACCEPTED")
        val operation = get("/api/operations/${receipt["operationId"].asText()}")
        assertThat(operation.status).isEqualTo(200)
        assertThat(operation.body["operation"]["status"].asText()).isEqualTo("SUCCEEDED")
        assertThat(get("/api/payments/$paymentId").body["status"].asText()).isEqualTo("CLOSED")
        assertThat(get("/api/payments/$paymentId").body["closeReason"].asText())
            .isEqualTo("PAYMENT_EXPIRED_WITHOUT_PENDING_ATTEMPT")

        val replay = post(path, body)
        assertThat(replay.status).isEqualTo(200)
        assertThat(replay.body["receipt"]["operationId"]).isEqualTo(receipt["operationId"])
        assertThat(replay.body["receipt"]["acceptanceStatus"].asText()).isEqualTo("ALREADY_ACCEPTED")
        assertThat(replay.body["receipt"]["idempotentReplay"].asBoolean()).isTrue()
        assertThat(operationCount(key)).isEqualTo(1)

        val rebound = post("/api/payments/$secondPaymentId/close-expired", body)
        assertThat(rebound.status).isEqualTo(409)
        assertThat(rebound.body["code"].asText()).isEqualTo("IDEMPOTENCY_CONFLICT")
        val wrongMerchant = post(path, body + ("merchantId" to "M-OTHER"))
        assertThat(wrongMerchant.status).isEqualTo(409)
        assertThat(wrongMerchant.body["code"].asText()).isEqualTo("PAYMENT_MERCHANT_CONFLICT")
        assertThat(operationCount(key)).isEqualTo(1)
    }

    @Test
    fun `pending attempt becomes unknown and opens one stable review after policy threshold`() {
        clock.set(Instant.parse("2026-09-25T00:00:00Z"))
        val paymentId = createPayment()
        val marker = UUID.randomUUID().toString()
        val attempt = post(
            "/api/payments/$paymentId/attempts",
            mapOf("idempotencyKey" to "attempt-$marker"),
        )
        assertThat(attempt.status).withFailMessage(attempt.body.toString()).isEqualTo(201)
        val attemptId = attempt.body["paymentAttemptId"].asText()
        val submission = post(
            "/api/payments/$paymentId/attempts/$attemptId/submissions",
            mapOf("idempotencyKey" to "submission-$marker"),
        )
        assertThat(submission.status).withFailMessage(submission.body.toString()).isEqualTo(200)
        assertThat(submission.body["paymentStatus"].asText()).isEqualTo("PROCESSING")

        clock.advance(Duration.ofMinutes(31))
        val first = post(
            "/api/payments/$paymentId/close-expired",
            mapOf("merchantId" to "M-001", "idempotencyKey" to "close-$marker"),
        )
        assertThat(first.status).withFailMessage(first.body.toString()).isEqualTo(200)
        assertThat(first.body["paymentStatus"].asText()).isEqualTo("RESULT_UNKNOWN")
        assertThat(get("/api/payments/$paymentId").body["attempts"][0]["status"].asText())
            .isEqualTo("RESULT_UNKNOWN")
        assertThat(get("/api/payments/$paymentId").body["reviews"].size()).isZero()

        clock.advance(Duration.ofMinutes(5))
        val reviewed = post(
            "/api/payments/$paymentId/close-expired",
            mapOf("merchantId" to "M-001", "idempotencyKey" to "close-review-$marker"),
        )
        assertThat(reviewed.status).withFailMessage(reviewed.body.toString()).isEqualTo(200)
        val payment = get("/api/payments/$paymentId").body
        assertThat(payment["reviews"].size()).isEqualTo(1)
        assertThat(payment["reviews"][0]["type"].asText()).isEqualTo("EXPIRY_RESULT_UNKNOWN")
        assertThat(payment["reviews"][0]["status"].asText()).isEqualTo("OPEN")
        assertThat(payment["settlementBlocked"].asBoolean()).isTrue()
        val reviewIdentity = payment["reviews"][0]["reviewIdentity"].asText()
        assertThat(
            jdbc.queryForObject(
                "select count(*) from manual_review_item where origin_identity = ?",
                Int::class.java, reviewIdentity,
            )
        ).isEqualTo(1)
        val replay = post(
            "/api/payments/$paymentId/close-expired",
            mapOf("merchantId" to "M-001", "idempotencyKey" to "close-review-$marker"),
        )
        assertThat(replay.body["receipt"]["operationId"]).isEqualTo(reviewed.body["receipt"]["operationId"])
        assertThat(get("/api/payments/$paymentId").body["reviews"].size()).isEqualTo(1)
    }

    @Test
    fun `succeeded payment and unknown payment reject without accepting an operation`() {
        clock.set(Instant.parse("2026-09-25T00:00:00Z"))
        val paymentId = createPayment()
        val key = "close-rejected-${UUID.randomUUID()}"
        jdbc.update("update payment set status = 2 where id = ?", paymentId)
        clock.advance(Duration.ofMinutes(31))
        val rejected = post(
            "/api/payments/$paymentId/close-expired",
            mapOf("merchantId" to "M-001", "idempotencyKey" to key),
        )
        assertThat(rejected.status).isEqualTo(409)
        assertThat(rejected.body["code"].asText()).isEqualTo("INVALID_STATE_TRANSITION")
        assertThat(operationCount(key)).isZero()

        val absentPaymentId = paymentId.dropLast(1) + if (paymentId.last() == 'a') 'b' else 'a'
        val missing = post(
            "/api/payments/$absentPaymentId/close-expired",
            mapOf("merchantId" to "M-001", "idempotencyKey" to key),
        )
        assertThat(missing.status).isEqualTo(404)
        assertThat(missing.body["code"].asText()).isEqualTo("PAYMENT_NOT_FOUND")
        assertThat(operationCount(key)).isZero()
    }

    private fun createPayment(): String {
        val marker = UUID.randomUUID().toString()
        val created = post(
            "/api/payments",
            mapOf(
                "merchantId" to "M-001",
                "merchantOrderNumber" to "expiry-$marker",
                "idempotencyKey" to "payment-$marker",
                "money" to mapOf("currency" to "CNY", "amountMinor" to "10000"),
                "paymentMethod" to "CARD",
            ),
        )
        assertThat(created.status).withFailMessage(created.body.toString()).isEqualTo(201)
        return created.body["paymentId"].asText()
    }

    private fun operationCount(key: String): Int =
        jdbc.queryForObject(
            "select count(*) from operation where command_type = ? and idempotency_key = ?",
            Int::class.java, "CloseExpiredPayment", key,
        )!!

    private fun get(path: String): HttpResult {
        val response = mvc.perform(mvcGet(path)).andReturn().response
        return HttpResult(response.status, json.readTree(response.contentAsByteArray))
    }

    private fun post(path: String, body: Any): HttpResult {
        val response = mvc.perform(
            mvcPost(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)),
        ).andReturn().response
        return HttpResult(response.status, json.readTree(response.contentAsByteArray))
    }

    private data class HttpResult(val status: Int, val body: JsonNode)
}
