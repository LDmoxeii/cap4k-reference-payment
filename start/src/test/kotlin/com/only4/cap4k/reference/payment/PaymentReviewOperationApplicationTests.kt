package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorRegistry
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceLogicalClock
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@SpringBootTest
@AutoConfigureMockMvc
class PaymentReviewOperationApplicationTests(
    @param:Autowired private val mvc: MockMvc,
    @param:Autowired private val json: ObjectMapper,
    @param:Autowired private val clock: ReferenceLogicalClock,
    @param:Autowired private val actors: ReferenceActorRegistry,
    @param:Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `trusted review actor and first decision time survive replay while changed content conflicts`() {
        val (paymentId, reviewId) = openAcceptedSuccessConflict()
        val key = "review-operation-${UUID.randomUUID()}"
        val decisionIdentity = "review-decision-${UUID.randomUUID()}"
        val path = "/api/payments/$paymentId/reviews/$reviewId/decisions"
        val request = decisionRequest(paymentId, reviewId, key, decisionIdentity) + mapOf(
            "actorId" to "forged-actor",
            "operatorIdentity" to "forged-operator",
            "operatorRole" to "ADMIN",
            "decidedAt" to "1999-01-01T00:00:00Z",
        )
        val initialOperations = operationCount(key)
        val initialDecisions = decisionCount(decisionIdentity)
        val before = payment(paymentId)
        val notificationCount = before["merchantSuccessNotificationIntentCount"].asInt()
        clock.set(Instant.parse("2026-09-23T10:00:00Z"))
        try {
            val first = postJson(path, request, ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS)
            assertThat(first.status).isEqualTo(200)
            assertThat(first.body["actorId"].asText()).isEqualTo("reference-payment-reviewer")
            assertThat(first.body["decidedAt"].asText()).isEqualTo("2026-09-23T10:00:00Z")
            assertThat(first.body["reason"].asText()).isEqualTo("retained accepted success")
            assertThat(first.body["evidence"].asText()).isEqualTo("ticket://payment-review/1")
            val receipt = first.body["receipt"]
            assertThat(receipt["acceptanceStatus"].asText()).isEqualTo("ACCEPTED")
            assertThat(receipt["idempotentReplay"].asBoolean()).isFalse()
            assertThat(receipt["readAfter"]["mode"].asText()).isEqualTo("READ_ONCE")
            assertThat(receipt["readAfter"]["resourceUrl"].asText()).isEqualTo("/api/payments/$paymentId")
            val operationId = receipt["operationId"].asText()
            val operation = getJson("/api/operations/$operationId")["operation"]
            assertThat(operation["status"].asText()).isEqualTo("SUCCEEDED")
            assertThat(operation["finality"].asText()).isEqualTo("FINAL")
            assertThat(operation["resource"]["resourceId"].asText()).isEqualTo(first.body["decisionId"].asText())

            clock.advance(Duration.ofHours(2))
            val replay = postJson(path, request, ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS)
            assertThat(replay.status).isEqualTo(200)
            assertThat(replay.body["decisionId"]).isEqualTo(first.body["decisionId"])
            assertThat(replay.body["decidedAt"]).isEqualTo(first.body["decidedAt"])
            assertThat(replay.body["receipt"]["operationId"].asText()).isEqualTo(operationId)
            assertThat(replay.body["receipt"]["acceptanceStatus"].asText()).isEqualTo("ALREADY_ACCEPTED")
            assertThat(replay.body["receipt"]["idempotentReplay"].asBoolean()).isTrue()

            listOf("reason" to "changed reason", "evidence" to "ticket://changed").forEach { (field, value) ->
                val conflict = postJson(path, request + (field to value), ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS)
                assertThat(conflict.status).isEqualTo(409)
                assertThat(conflict.body["code"].asText()).isEqualTo("IDEMPOTENCY_CONFLICT")
            }
            val alternateAlias = "payment-reviewer-${UUID.randomUUID()}"
            actors.register(alternateAlias, "alternate-payment-reviewer", "PAYMENT_REVIEW_OPERATOR")
            val changedActor = postJson(path, request, alternateAlias)
            assertThat(changedActor.status).isEqualTo(409)
            assertThat(changedActor.body["code"].asText()).isEqualTo("IDEMPOTENCY_CONFLICT")

            val stored = payment(paymentId)
            val decision = stored["reviews"].first { it["reviewIdentity"].asText() == reviewId }["decisions"].single()
            assertThat(decision["operatorIdentity"].asText()).isEqualTo("reference-payment-reviewer")
            assertThat(decision["authorizationOutcome"].asBoolean()).isTrue()
            assertThat(decision["decidedAt"].asText()).isEqualTo(first.body["decidedAt"].asText())
            assertThat(decision["reason"].asText()).isEqualTo(first.body["reason"].asText())
            assertThat(decision["evidence"].asText()).isEqualTo(first.body["evidence"].asText())
            assertThat(stored["merchantSuccessNotificationIntentCount"].asInt()).isEqualTo(notificationCount)
            assertThat(operationCount(key)).isEqualTo(initialOperations + 1)
            assertThat(decisionCount(decisionIdentity)).isEqualTo(initialDecisions + 1)
        } finally {
            clock.reset()
        }
    }

    @Test
    fun `missing unknown and unauthorized actor or absent key reject before acceptance`() {
        val (paymentId, reviewId) = openAcceptedSuccessConflict()
        val key = "review-rejected-${UUID.randomUUID()}"
        val decisionIdentity = "review-rejected-decision-${UUID.randomUUID()}"
        val path = "/api/payments/$paymentId/reviews/$reviewId/decisions"
        val request = decisionRequest(paymentId, reviewId, key, decisionIdentity)
        val initialOperations = operationCount(key)
        val initialDecisions = decisionCount(decisionIdentity)
        val before = payment(paymentId)["merchantSuccessNotificationIntentCount"].asInt()

        listOf(null, "unknown-${UUID.randomUUID()}", ReferenceActorRegistry.REFUND_REVIEWER_ALIAS).forEach { actor ->
            val rejected = postJson(path, request, actor)
            assertThat(rejected.status).isEqualTo(400)
            assertThat(rejected.body["code"].asText()).isEqualTo("VALIDATION_ERROR")
        }
        val missingKey = postJson(path, request - "idempotencyKey", ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS)
        assertThat(missingKey.status).isEqualTo(400)
        assertThat(operationCount(key)).isEqualTo(initialOperations)
        assertThat(decisionCount(decisionIdentity)).isEqualTo(initialDecisions)
        assertThat(payment(paymentId)["merchantSuccessNotificationIntentCount"].asInt()).isEqualTo(before)
    }

    private fun openAcceptedSuccessConflict(): Pair<String, String> {
        val marker = UUID.randomUUID().toString()
        val paymentId = postJson(
            "/api/payments",
            mapOf(
                "merchantId" to "M-001",
                "merchantOrderNumber" to "review-$marker",
                "idempotencyKey" to "review-create-$marker",
                "money" to mapOf("currency" to "CNY", "amountMinor" to "10000"),
                "paymentMethod" to "CARD",
                "expiresAt" to Instant.parse("2030-01-01T00:00:00Z"),
            ),
        ).success()["paymentId"].asText()
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts", mapOf("idempotencyKey" to "review-attempt-$marker"),
        ).success()["paymentAttemptId"].asText()
        postJson(
            "/api/payments/$paymentId/attempts/$attemptId/submissions",
            mapOf("idempotencyKey" to "review-submit-$marker"),
        ).success()
        val transactionId = "review-transaction-$marker"
        listOf("SUCCESS", "FAILED").forEachIndexed { index, result ->
            val notificationId = "review-notification-$index-$marker"
            val rawPayload = "review-raw-$index-$marker"
            postJson(
                "/api/reference-fixtures/callback-evidence",
                mapOf(
                    "idempotencyKey" to "review-evidence-$notificationId",
                    "kind" to "PAYMENT",
                    "channelId" to "C-001",
                    "externalIdentity" to notificationId,
                    "associationIdentity" to "$paymentId|$attemptId|$transactionId",
                    "money" to mapOf("currency" to "CNY", "amountMinor" to "10000"),
                    "rawPayload" to rawPayload,
                ),
            ).success()
            postJson(
                "/api/channel/payment-results",
                mapOf(
                    "channelId" to "C-001",
                    "notificationId" to notificationId,
                    "paymentId" to paymentId,
                    "paymentAttemptId" to attemptId,
                    "channelTransactionId" to transactionId,
                    "money" to mapOf("currency" to "CNY", "amountMinor" to "10000"),
                    "result" to result,
                    "occurredAt" to Instant.parse("2026-09-22T01:00:00Z").plusSeconds(index.toLong()),
                    "rawPayload" to rawPayload,
                ),
            ).success()
        }
        val review = payment(paymentId)["reviews"].first {
            it["type"].asText() == "FAILURE_OR_UNKNOWN_AFTER_SUCCESS"
        }
        return paymentId to review["reviewIdentity"].asText()
    }

    private fun decisionRequest(paymentId: String, reviewId: String, key: String, identity: String): Map<String, Any?> =
        mapOf(
            "paymentId" to paymentId,
            "merchantId" to "M-001",
            "reviewId" to reviewId,
            "idempotencyKey" to key,
            "decisionIdentity" to identity,
            "decision" to "KEEP_ACCEPTED_SUCCESS_WITH_REMEDIATION",
            "reason" to "retained accepted success",
            "evidence" to "ticket://payment-review/1",
            "eligibilityImpact" to "ALLOW_SETTLEMENT",
            "remediationReference" to "ticket://payment-review/remediation",
        )

    private fun payment(id: String): JsonNode = getJson("/api/payments/$id")

    private fun getJson(path: String): JsonNode {
        val response = mvc.perform(get(path)).andReturn().response
        assertThat(response.status).withFailMessage(response.contentAsString).isEqualTo(200)
        return json.readTree(response.contentAsByteArray)
    }

    private fun postJson(path: String, body: Any, actor: String? = null): HttpResult {
        val request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))
        actor?.let { request.header("X-Reference-Actor-Context", it) }
        val response = mvc.perform(request).andReturn().response
        return HttpResult(response.status, json.readTree(response.contentAsByteArray))
    }

    private fun operationCount(key: String): Int =
        jdbc.queryForObject(
            "select count(*) from operation where command_type = ? and idempotency_key = ?",
            Int::class.java, "AdjudicatePaymentReview", key,
        )!!

    private fun decisionCount(identity: String): Int =
        jdbc.queryForObject(
            "select count(*) from payment_review_decision where decision_identity = ?",
            Int::class.java, identity,
        )!!

    private data class HttpResult(val status: Int, val body: JsonNode) {
        fun success(): JsonNode {
            assertThat(status).withFailMessage(body.toString()).isBetween(200, 299)
            return body
        }
    }
}
