package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceLogicalClock
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.contract.common.OperationStatus
import com.only4.cap4k.reference.payment.contract.common.ReadAfterMode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.http.MediaType

@SpringBootTest
@AutoConfigureMockMvc
class OperationProtocolApplicationTests(
    @param:Autowired private val mvc: MockMvc,
    @param:Autowired private val json: ObjectMapper,
    @param:Autowired private val clock: ReferenceLogicalClock,
    @param:Autowired private val policy: ReferencePolicyService,
) {
    @Test
    fun `READ_ONCE receipt has immediate resource and Operation is readable`() {
        val url = "/api/reference-fixtures/clock"
        val receipt = accept(ReadAfterMode.READ_ONCE, url)
        assertThat(receipt.readAfter.operationUrl).isEqualTo("/api/operations/${receipt.operationId}")
        assertThat(receipt.readAfter.resourceUrl).isEqualTo(url)
        assertThat(receipt.readAfter.retryAfterMs).isZero()
        val operation = operation(receipt.operationId)
        assertThat(operation["status"].asText()).isEqualTo("SUCCEEDED")
        assertThat(operation["finality"].asText()).isEqualTo("FINAL")
        assertThat(operation["resourceUrl"].asText()).isEqualTo(url)
        assertThat(operation["updatedAt"].asText()).isNotBlank()
        assertThat(operation["readAfter"]["operationUrl"].asText()).isEqualTo(receipt.readAfter.operationUrl)
    }

    @Test
    fun `POLL is readable at receipt and three independent branches converge`() {
        val retry = policy.current().operationPollRetryAfterMs.toLong()
        val resource = "/api/reference-fixtures/operations/absent/resource"
        listOf(
            "SUCCEEDED" to "SUCCEEDED",
            "FAILED" to "FAILED",
            "REVIEW_REQUIRED" to "REVIEW_REQUIRED",
        ).forEach { (outcome, expected) ->
            val receipt = accept(ReadAfterMode.POLL, resource)
            assertThat(receipt.readAfter.mode).isEqualTo(ReadAfterMode.POLL)
            assertThat(receipt.readAfter.operationUrl).isEqualTo("/api/operations/${receipt.operationId}")
            assertThat(receipt.readAfter.retryAfterMs).isEqualTo(retry)
            assertThat(operation(receipt.operationId)["status"].asText()).isEqualTo("ACCEPTED")
            transition(receipt.operationId, "PROCESSING")
            assertThat(operation(receipt.operationId)["status"].asText()).isEqualTo("PROCESSING")
            transition(receipt.operationId, outcome, resource)
            val terminal = operation(receipt.operationId)
            assertThat(terminal["status"].asText()).isEqualTo(expected)
            assertThat(terminal["completedAt"].asText()).isNotBlank()
            assertThat(terminal["readAfter"]["retryAfterMs"].asLong()).isEqualTo(retry)
            when (outcome) {
                "SUCCEEDED" -> {
                    assertThat(terminal["finality"].asText()).isEqualTo("FINAL")
                    assertThat(terminal["result"]["fixtureId"].asText()).isEqualTo(receipt.operationId)
                }
                "FAILED" -> {
                    assertThat(terminal["finality"].asText()).isEqualTo("FINAL")
                    assertThat(terminal["error"]["code"].asText()).isEqualTo("REFERENCE_FIXTURE_FAILED")
                    assertThat(terminal["error"]["details"]["source"].asText()).isEqualTo("fixture")
                    assertThat(terminal["error"]["correlationId"].asText()).isEqualTo(receipt.operationId)
                    assertThat(terminal["error"]["retryable"].asBoolean()).isFalse()
                }
                else -> {
                    assertThat(terminal["finality"].asText()).isEqualTo("REVIEW_REQUIRED")
                    assertThat(terminal["reviewId"].asText()).isEqualTo("review-${receipt.operationId}")
                }
            }
            assertThatThrownBy { transition(receipt.operationId, "PROCESSING") }
                .hasMessageContaining("INVALID_STATE_TRANSITION")
        }
    }

    @Test
    fun `not ready resource is retryable while Operation remains unchanged`() {
        val receipt = accept(ReadAfterMode.POLL)
        val before = operation(receipt.operationId)
        val response = mvc.perform(get(requireNotNull(receipt.readAfter.resourceUrl))).andReturn().response
        assertThat(response.status).isEqualTo(409)
        val error = json.readTree(response.contentAsByteArray)
        assertThat(error["code"].asText()).isEqualTo("RESOURCE_NOT_READY")
        assertThat(error["retryable"].asBoolean()).isTrue()
        assertThat(error["details"]["operationId"].asText()).isEqualTo(receipt.operationId)
        assertThat(error["details"]["retryAfterMs"].asLong()).isEqualTo(receipt.readAfter.retryAfterMs)
        val after = operation(receipt.operationId)
        assertThat(after["status"]).isEqualTo(before["status"])
        assertThat(after["updatedAt"]).isEqualTo(before["updatedAt"])
    }

    @Test
    fun `PT30S client observation timeout leaves same Operation pending and later progress possible`() {
        clock.set(Instant.parse("2026-09-23T00:00:00Z"))
        try {
            val receipt = accept(ReadAfterMode.POLL)
            val observingFrom = clock.instant()
            clock.advance(policy.current().operationObservationTimeout)
            val observation = if (Duration.between(observingFrom, clock.instant()) >=
                policy.current().operationObservationTimeout) "OBSERVATION_TIMEOUT" else "PENDING"
            assertThat(observation).isEqualTo("OBSERVATION_TIMEOUT")
            assertThat(operation(receipt.operationId)["status"].asText()).isEqualTo(OperationStatus.ACCEPTED.name)
            transition(receipt.operationId, "PROCESSING")
            assertThat(operation(receipt.operationId)["status"].asText()).isEqualTo(OperationStatus.PROCESSING.name)
            clock.advance(policy.current().operationObservationTimeout)
            assertThat(operation(receipt.operationId)["status"].asText()).isEqualTo(OperationStatus.PROCESSING.name)
            transition(receipt.operationId, "SUCCEEDED", "/api/reference-fixtures/clock")
            assertThat(operation(receipt.operationId)["status"].asText()).isEqualTo(OperationStatus.SUCCEEDED.name)
        } finally {
            clock.reset()
        }
    }

    @Test
    fun `unknown Operation is stable NOT_FOUND not VALIDATION_ERROR`() {
        val existing = accept(ReadAfterMode.POLL).operationId
        val missing = existing.dropLast(1) + if (existing.last() == 'a') 'b' else 'a'
        val response = mvc.perform(get("/api/operations/$missing")).andReturn().response
        assertThat(response.status).isEqualTo(404)
        val error = json.readTree(response.contentAsByteArray)
        assertThat(error["code"].asText()).isEqualTo("NOT_FOUND")
        assertThat(error["retryable"].asBoolean()).isFalse()
        assertThat(error["details"]["operationId"].asText()).isEqualTo(missing)
    }

    private fun accept(mode: ReadAfterMode, resourceUrl: String? = null): OperationReceipt {
        val identity = "operation-${UUID.randomUUID()}"
        val response = postJson(
            "/api/reference-fixtures/operations",
            mapOf(
                "merchantId" to "M-001",
                "commandType" to "ReferenceFixtureOperation",
                "idempotencyKey" to identity,
                "resourceType" to "REFERENCE_FIXTURE",
                "resourceId" to identity,
                "readAfterMode" to mode.name,
                "resourceUrl" to resourceUrl,
            ),
        )
        return json.treeToValue(response["receipt"], OperationReceipt::class.java)
    }

    private fun transition(id: String, outcome: String, resourceUrl: String? = null): JsonNode = postJson(
        "/api/reference-fixtures/operations/$id/transitions",
        buildMap<String, Any?> {
            put("outcome", outcome)
            put("resourceUrl", resourceUrl)
            when (outcome) {
                "SUCCEEDED" -> put("result", mapOf("fixtureId" to id))
                "FAILED" -> {
                    put("errorCode", "REFERENCE_FIXTURE_FAILED")
                    put("errorMessage", "fixture 异步失败")
                    put("errorDetails", mapOf("source" to "fixture"))
                    put("retryable", false)
                }
                "REVIEW_REQUIRED" -> put("reviewId", "review-$id")
            }
        },
    )

    private fun operation(id: String): JsonNode {
        val response = mvc.perform(get("/api/operations/$id")).andReturn().response
        assertThat(response.status).withFailMessage(response.contentAsString).isEqualTo(200)
        val body = json.readTree(response.contentAsByteArray)
        return body["operation"] ?: body
    }

    private fun postJson(path: String, body: Any): JsonNode {
        val response = mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)),
        ).andReturn().response
        assertThat(response.status).withFailMessage(response.contentAsString).isBetween(200, 299)
        return json.readTree(response.contentAsByteArray)
    }
}
