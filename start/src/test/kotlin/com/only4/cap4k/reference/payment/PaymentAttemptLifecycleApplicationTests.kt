package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Exercises the public split attempt contract.  The reference channel fixture only decides
 * submission acceptance; it must never manufacture a payment-success fact.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentAttemptLifecycleApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val objectMapper: ObjectMapper,
) {

    @AfterEach
    fun resetReferenceChannelScript() {
        postJson(
            path = "/api/reference-fixtures/payment-channel-script/reset",
            payload = mapOf("channelId" to CHANNEL_ID),
            expectedStatus = 200,
        )
    }

    @Test
    @DisplayName("PAY-AC-010/016/100 — 显式创建、受理、详情回读与幂等重放不形成支付成功")
    fun `explicit attempt creation submission and replay preserve channel acceptance without payment success`() {
        configureChannel("ACCEPT_THEN_SUCCESS")
        val scope = nextScope("accepted")
        val payment = createPayment(scope)
        val paymentId = payment.requiredText("paymentId")

        assertThat(payment.requiredText("status")).isEqualTo("PAYABLE")
        assertThat(payment["receipt"].requiredText("commandType")).isEqualTo("CreatePaymentIntent")

        val beforeAttempt = getJson("/api/payments/$paymentId")
        assertThat(beforeAttempt.requiredText("status")).isEqualTo("PAYABLE")
        assertThat(beforeAttempt["money"].requiredText("currency")).isEqualTo("CNY")
        assertThat(beforeAttempt["money"].requiredText("amountMinor")).isEqualTo("10000")
        assertThat(beforeAttempt["successFactFormed"].asBoolean()).isFalse()
        assertThat(beforeAttempt["attemptCount"].asInt()).isZero()

        val createBody = mapOf("idempotencyKey" to "attempt-create-$scope")
        val createdAttempt = postJson(
            path = "/api/payments/$paymentId/attempts",
            payload = createBody,
            expectedStatus = 201,
        )
        val attemptId = createdAttempt.requiredText("paymentAttemptId")
        assertThat(createdAttempt.requiredText("paymentStatus")).isEqualTo("PAYABLE")
        assertThat(createdAttempt.requiredText("attemptStatus")).isEqualTo("CREATED")
        assertThat(createdAttempt.requiredText("channelId")).isEqualTo(CHANNEL_ID)
        assertThat(createdAttempt.requiredText("requestIdentity")).isNotBlank()
        assertThat(createdAttempt.requiredText("interactionInformation")).isNotBlank()
        assertThat(createdAttempt["riskReason"].isNull).isTrue()
        assertThat(createdAttempt["receipt"].requiredText("idempotentReplay")).isEqualTo("false")

        val createReplay = postJson(
            path = "/api/payments/$paymentId/attempts",
            payload = createBody,
            expectedStatus = 201,
        )
        assertThat(createReplay.requiredText("paymentAttemptId")).isEqualTo(attemptId)
        assertThat(createReplay["receipt"].requiredText("operationId"))
            .isEqualTo(createdAttempt["receipt"].requiredText("operationId"))
        assertThat(createReplay["receipt"]["idempotentReplay"].asBoolean()).isTrue()
        assertThat(createReplay["receipt"].requiredText("acceptanceStatus")).isEqualTo("ALREADY_ACCEPTED")

        val createConflict = postJsonResult(
            path = "/api/payments/$paymentId/attempts",
            payload = mapOf(
                "idempotencyKey" to "attempt-create-$scope",
                "riskReason" to "same key but a different command payload",
            ),
        )
        assertThat(createConflict.status).isEqualTo(409)
        assertThat(createConflict.body.requiredText("code")).isEqualTo("IDEMPOTENCY_CONFLICT")

        val submitBody = mapOf("idempotencyKey" to "attempt-submit-$scope")
        val submitted = postJson(
            path = "/api/payments/$paymentId/attempts/$attemptId/submissions",
            payload = submitBody,
            expectedStatus = 200,
        )
        assertThat(submitted.requiredText("paymentId")).isEqualTo(paymentId)
        assertThat(submitted.requiredText("paymentAttemptId")).isEqualTo(attemptId)
        assertThat(submitted.requiredText("attemptStatus")).isEqualTo("ACCEPTED")
        assertThat(submitted.requiredText("paymentStatus")).isEqualTo("PROCESSING")
        assertThat(submitted.requiredText("submissionOutcome")).isEqualTo("ACCEPTED")
        assertThat(submitted.requiredText("submissionIdentity")).isNotBlank()
        assertThat(submitted.requiredText("interactionInformation")).isNotBlank()

        val submitReplay = postJson(
            path = "/api/payments/$paymentId/attempts/$attemptId/submissions",
            payload = submitBody,
            expectedStatus = 200,
        )
        assertThat(submitReplay.requiredText("submissionIdentity"))
            .isEqualTo(submitted.requiredText("submissionIdentity"))
        assertThat(submitReplay["receipt"].requiredText("operationId"))
            .isEqualTo(submitted["receipt"].requiredText("operationId"))
        assertThat(submitReplay["receipt"]["idempotentReplay"].asBoolean()).isTrue()

        val detail = getJson("/api/payments/$paymentId")
        assertThat(detail.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(detail["successFactFormed"].asBoolean()).isFalse()
        assertThat(detail["succeededAt"].isNull).isTrue()
        assertThat(detail["attempts"]).hasSize(1)
        val attempt = detail["attempts"][0]
        assertThat(attempt.requiredText("status")).isEqualTo("ACCEPTED")
        assertThat(attempt.requiredText("submissionIdentity"))
            .isEqualTo(submitted.requiredText("submissionIdentity"))
        assertThat(attempt.requiredText("submittedAt")).isNotBlank()
        assertThat(attempt.requiredText("acceptedAt")).isNotBlank()
        assertThat(attempt["completedAt"].isNull).isTrue()
        assertThat(attempt.requiredText("interactionInformation")).isNotBlank()
        assertThat(attempt["submissionReceipts"]).hasSize(1)
        val submissionReceipt = attempt["submissionReceipts"][0]
        assertThat(submissionReceipt.requiredText("submissionIdentity"))
            .isEqualTo(submitted.requiredText("submissionIdentity"))
        assertThat(submissionReceipt.requiredText("requestIdentity"))
            .isEqualTo(createdAttempt.requiredText("requestIdentity"))
        assertThat(submissionReceipt.requiredText("channelId")).isEqualTo(CHANNEL_ID)
        assertThat(submissionReceipt.requiredText("submittedAt")).isNotBlank()
        assertThat(submissionReceipt.requiredText("outcome")).isEqualTo("ACCEPTED")
    }

    @Test
    @DisplayName("PAY-AC-010/100 — 提交结果未知保留 attempt，且不将未知伪装为支付成功")
    fun `unknown submission result remains explicit and does not create a payment success fact`() {
        configureChannel("NO_RESULT")
        val scope = nextScope("unknown")
        val paymentId = createPayment(scope).requiredText("paymentId")
        val attemptId = postJson(
            path = "/api/payments/$paymentId/attempts",
            payload = mapOf("idempotencyKey" to "attempt-create-$scope"),
            expectedStatus = 201,
        ).requiredText("paymentAttemptId")

        val submitted = postJson(
            path = "/api/payments/$paymentId/attempts/$attemptId/submissions",
            payload = mapOf("idempotencyKey" to "attempt-submit-$scope"),
            expectedStatus = 200,
        )
        assertThat(submitted.requiredText("attemptStatus")).isEqualTo("RESULT_UNKNOWN")
        assertThat(submitted.requiredText("paymentStatus")).isEqualTo("RESULT_UNKNOWN")
        assertThat(submitted.requiredText("submissionOutcome")).isEqualTo("RESULT_UNKNOWN")

        val detail = getJson("/api/payments/$paymentId")
        assertThat(detail.requiredText("status")).isEqualTo("RESULT_UNKNOWN")
        assertThat(detail["successFactFormed"].asBoolean()).isFalse()
        val attempt = detail["attempts"].single()
        assertThat(attempt.requiredText("status")).isEqualTo("RESULT_UNKNOWN")
        assertThat(attempt.requiredText("completedAt")).isNotBlank()
        assertThat(attempt["submissionReceipts"]).hasSize(1)
        assertThat(attempt["submissionReceipts"][0].requiredText("outcome")).isEqualTo("RESULT_UNKNOWN")
    }

    private fun configureChannel(script: String) {
        val response = postJson(
            path = "/api/reference-fixtures/payment-channel-script",
            payload = mapOf("channelId" to CHANNEL_ID, "script" to script),
            expectedStatus = 200,
        )
        assertThat(response.requiredText("channelId")).isEqualTo(CHANNEL_ID)
        assertThat(response.requiredText("script")).isEqualTo(script)
    }

    private fun createPayment(scope: String): JsonNode = postJson(
        path = "/api/payments",
        payload = mapOf(
            "merchantId" to "M-001",
            "merchantOrderNumber" to "payment-attempt-$scope",
            "idempotencyKey" to "payment-create-$scope",
            "money" to mapOf("currency" to "CNY", "amountMinor" to "10000"),
            "paymentMethod" to "CARD",
            "expiresAt" to Instant.parse("2030-01-01T00:00:00Z"),
        ),
        expectedStatus = 201,
    )

    private fun postJson(path: String, payload: Any, expectedStatus: Int): JsonNode {
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)),
        )
            .andExpect(status().`is`(expectedStatus))
            .andReturn()
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun postJsonResult(path: String, payload: Any): HttpJsonResult {
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)),
        ).andReturn()
        return HttpJsonResult(
            status = result.response.status,
            body = objectMapper.readTree(result.response.contentAsByteArray),
        )
    }

    private fun getJson(path: String): JsonNode {
        val result = mockMvc.perform(get(path))
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun JsonNode.requiredText(field: String): String =
        requireNotNull(get(field)) { "missing JSON field $field in $this" }.asText()

    private data class HttpJsonResult(val status: Int, val body: JsonNode)

    private companion object {
        const val CHANNEL_ID = "C-001"
        private val sequence = AtomicInteger()

        fun nextScope(label: String): String = "$label-${sequence.incrementAndGet()}"
    }
}
