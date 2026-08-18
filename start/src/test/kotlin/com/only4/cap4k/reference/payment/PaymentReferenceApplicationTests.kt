package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@AutoConfigureMockMvc
class PaymentReferenceApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val objectMapper: ObjectMapper,
    @param:Autowired private val entityManager: EntityManager,
    @param:Autowired private val transactionManager: PlatformTransactionManager,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `create attempt confirm duplicate conflict and query form one durable payment chain`() {
        val createRequest = paymentRequest(
            merchantOrderNumber = "O-001",
            idempotencyKey = "K-001",
            amount = "100.00",
        )

        val created = postJson("/api/payments", createRequest, expectedStatus = 201)
        val paymentId = created.requiredText("paymentId")
        assertThat(created.requiredText("status")).isEqualTo("PENDING")
        assertThat(created["idempotentReplay"].asBoolean()).isFalse()

        val replay = postJson("/api/payments", createRequest, expectedStatus = 201)
        assertThat(replay.requiredText("paymentId")).isEqualTo(paymentId)
        assertThat(replay["idempotentReplay"].asBoolean()).isTrue()

        val conflict = postJson(
            "/api/payments",
            createRequest + ("amount" to BigDecimal("120.00")),
            expectedStatus = 409,
        )
        assertThat(conflict.requiredText("code")).isEqualTo("IDEMPOTENCY_CONFLICT")

        val beforeAttempt = getJson("/api/payments/$paymentId")
        assertThat(beforeAttempt["amount"].decimalValue()).isEqualByComparingTo("100.00")
        assertThat(beforeAttempt.requiredText("currency")).isEqualTo("CNY")
        assertThat(beforeAttempt.requiredText("status")).isEqualTo("PENDING")
        assertThat(beforeAttempt["attemptCount"].asInt()).isZero()

        val attempt = postJson("/api/payments/$paymentId/attempts", emptyMap<String, Any>(), expectedStatus = 200)
        val attemptId = attempt.requiredText("paymentAttemptId")
        assertThat(attempt.requiredText("channelId")).isEqualTo("C-001")
        assertThat(attempt.requiredText("paymentStatus")).isEqualTo("PROCESSING")
        assertThat(attempt.requiredText("attemptStatus")).isEqualTo("PROCESSING")

        val callback = mapOf(
            "channelId" to "C-001",
            "notificationId" to "N-001",
            "paymentId" to paymentId,
            "paymentAttemptId" to attemptId,
            "channelTransactionId" to "CT-001",
            "amount" to BigDecimal("100.00"),
            "currency" to "CNY",
            "result" to "SUCCESS",
            "occurredAt" to Instant.parse("2026-08-17T08:00:00Z"),
            "verificationMaterial" to "wrong-secret",
        )
        val untrusted = postJson("/api/channel/payment-results", callback, expectedStatus = 200)
        assertThat(untrusted["accepted"].asBoolean()).isFalse()
        assertThat(untrusted["rejected"].asBoolean()).isTrue()
        assertThat(untrusted.requiredText("disposition")).isEqualTo("REJECTED")
        assertThat(untrusted.requiredText("paymentStatus")).isEqualTo("PROCESSING")
        assertThat(untrusted.requiredText("rejectionSummary")).contains("verification failed")

        val amountMismatch = postJson(
            "/api/channel/payment-results",
            callback + mapOf(
                "notificationId" to "N-002",
                "amount" to BigDecimal("99.99"),
                "verificationMaterial" to "test-secret",
            ),
            expectedStatus = 200,
        )
        assertThat(amountMismatch["accepted"].asBoolean()).isFalse()
        assertThat(amountMismatch.requiredText("rejectionSummary")).contains("does not match payment amount")

        val accepted = postJson(
            "/api/channel/payment-results",
            callback + mapOf(
                "notificationId" to "N-003",
                "verificationMaterial" to "test-secret",
            ),
            expectedStatus = 200,
        )
        assertThat(accepted["accepted"].asBoolean()).isTrue()
        assertThat(accepted["duplicate"].asBoolean()).isFalse()
        assertThat(accepted.requiredText("disposition")).isEqualTo("SUCCESS_ACCEPTED")
        assertThat(accepted["successFactFormedNow"].asBoolean()).isTrue()
        assertThat(accepted.requiredText("paymentStatus")).isEqualTo("SUCCEEDED")
        assertThat(accepted.requiredText("attemptStatus")).isEqualTo("SUCCEEDED")

        var duplicate: JsonNode? = null
        repeat(3) {
            duplicate = postJson(
                "/api/channel/payment-results",
                callback + mapOf(
                    "notificationId" to "N-003",
                    "verificationMaterial" to "test-secret",
                ),
                expectedStatus = 200,
            )
        }
        assertThat(requireNotNull(duplicate)["accepted"].asBoolean()).isTrue()
        assertThat(requireNotNull(duplicate)["duplicate"].asBoolean()).isTrue()
        assertThat(requireNotNull(duplicate).requiredText("disposition")).isEqualTo("ACCEPTED_DUPLICATE")
        assertThat(requireNotNull(duplicate)["successFactFormedNow"].asBoolean()).isFalse()
        assertThat(requireNotNull(duplicate)["notificationReceiveCount"].asInt()).isEqualTo(6)

        val conflictingFailure = postJson(
            "/api/channel/payment-results",
            callback + mapOf(
                "notificationId" to "N-004",
                "channelTransactionId" to "CT-002",
                "result" to "FAILED",
                "verificationMaterial" to "test-secret",
            ),
            expectedStatus = 200,
        )
        assertThat(conflictingFailure["accepted"].asBoolean()).isFalse()
        assertThat(conflictingFailure["conflicting"].asBoolean()).isTrue()
        assertThat(conflictingFailure.requiredText("disposition")).isEqualTo("CONFLICT")
        assertThat(conflictingFailure.requiredText("paymentStatus")).isEqualTo("SUCCEEDED")
        assertThat(conflictingFailure.requiredText("conflictSummary")).contains("conflicts with finalized attempt")

        val forbiddenAttempt = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 409,
        )
        assertThat(forbiddenAttempt.requiredText("code")).isEqualTo("PAYMENT_STATE_CONFLICT")

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(payment["amount"].decimalValue()).isEqualByComparingTo("100.00")
        assertThat(payment["attemptCount"].asInt()).isEqualTo(1)
        assertThat(payment["notificationReceiveCount"].asInt()).isEqualTo(7)
        assertThat(payment["rejectedNotificationCount"].asInt()).isEqualTo(2)
        assertThat(payment["conflictingNotificationCount"].asInt()).isEqualTo(1)
        assertThat(payment.requiredText("lastNotificationIdentity")).isEqualTo("N-004")
        assertThat(payment.requiredText("lastNotificationReceivedAt")).isNotBlank()
        assertThat(payment["successFactFormed"].asBoolean()).isTrue()
        assertThat(payment["merchantSuccessNotificationIntentCount"].asInt()).isEqualTo(1)
        assertThat(payment["settlementBlocked"].asBoolean()).isTrue()
        assertThat(payment.requiredText("createdAt")).isNotBlank()
        assertThat(payment.requiredText("expiresAt")).isEqualTo("2030-01-01T00:00:00Z")
        assertThat(payment.requiredText("succeededAt")).isEqualTo("2026-08-17T08:00:00Z")
        assertThat(payment.requiredText("channelTransactionId")).isEqualTo("CT-001")
        assertThat(payment["attempts"]).hasSize(1)
        val persistedAttempt = payment["attempts"][0]
        assertThat(persistedAttempt.requiredText("paymentAttemptId")).isEqualTo(attemptId)
        assertThat(persistedAttempt.requiredText("channelTransactionId")).isEqualTo("CT-001")
        assertThat(persistedAttempt.requiredText("initiatedAt")).isNotBlank()
        assertThat(persistedAttempt.requiredText("finalResult")).isEqualTo("SUCCESS")
        assertThat(persistedAttempt.requiredText("resultOccurredAt")).isEqualTo("2026-08-17T08:00:00Z")
        assertThat(persistedAttempt["notificationReceiveCount"].asInt()).isEqualTo(7)
        assertThat(persistedAttempt["verifiedNotificationCount"].asInt()).isEqualTo(1)
        assertThat(persistedAttempt["rejectedNotificationCount"].asInt()).isEqualTo(2)
        assertThat(persistedAttempt["conflictingNotificationCount"].asInt()).isEqualTo(1)
        assertThat(persistedAttempt.requiredText("notificationFirstReceivedAt")).isNotBlank()
        assertThat(persistedAttempt.requiredText("notificationLastReceivedAt")).isNotBlank()
        assertThat(persistedAttempt["notificationReceipts"]).hasSize(4)
        val acceptedReceipt = persistedAttempt["notificationReceipts"].first { it.requiredText("notificationIdentity") == "N-003" }
        assertThat(acceptedReceipt["receiveCount"].asInt()).isEqualTo(4)
        assertThat(acceptedReceipt["verified"].asBoolean()).isTrue()
        assertThat(acceptedReceipt["accepted"].asBoolean()).isTrue()
        assertThat(acceptedReceipt.requiredText("decision")).isEqualTo("ACCEPTED_DUPLICATE")

        val storedDecision = requireNotNull(
            jdbcTemplate.queryForObject(
                """
                select decision
                from payment_notification_receipt
                where notification_identity = ?
                """.trimIndent(),
                Int::class.java,
                "N-003",
            )
        )
        assertThat(storedDecision).isEqualTo(ChannelResultDisposition.ACCEPTED_DUPLICATE.value)

        TransactionTemplate(transactionManager).executeWithoutResult {
            entityManager.clear()
            val reloaded = requireNotNull(entityManager.find(Payment::class.java, PaymentId.parse(paymentId)))
            val receipt = reloaded.attempts.single()
                .paymentNotificationReceipts
                .single { it.notificationIdentity == "N-003" }

            assertThat(receipt.decision).isEqualTo(ChannelResultDisposition.ACCEPTED_DUPLICATE)
            assertThat(receipt.decision.group).isEqualTo("accepted")
            assertThat(receipt.decision.terminal).isTrue()
        }
    }

    @Test
    fun `invalid amount precision and unsupported currency never reserve an idempotency key`() {
        val base = paymentRequest(
            merchantOrderNumber = "O-INVALID",
            idempotencyKey = "K-INVALID",
            amount = "10.00",
        )

        assertThat(
            postJson("/api/payments", base + ("amount" to BigDecimal.ZERO), expectedStatus = 400)
                .requiredText("code")
        ).isEqualTo("INVALID_REQUEST")
        assertThat(
            postJson("/api/payments", base + ("amount" to BigDecimal("10.001")), expectedStatus = 400)
                .requiredText("code")
        ).isEqualTo("INVALID_REQUEST")
        assertThat(
            postJson("/api/payments", base + ("currency" to "USD"), expectedStatus = 400)
                .requiredText("code")
        ).isEqualTo("INVALID_REQUEST")

        val valid = postJson("/api/payments", base, expectedStatus = 201)
        assertThat(valid.requiredText("status")).isEqualTo("PENDING")
        assertThat(valid["idempotentReplay"].asBoolean()).isFalse()
    }


    @Test
    fun `attempt identity and currency mismatches remain queryable without advancing payment`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-MISMATCH", "K-MISMATCH", "30.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        ).requiredText("paymentAttemptId")
        val baseCallback = mapOf(
            "channelId" to "C-001",
            "notificationId" to "N-MISSING-ATTEMPT",
            "paymentId" to paymentId,
            "paymentAttemptId" to "018f22a0-0000-7000-8000-000000000099",
            "channelTransactionId" to "CT-MISMATCH",
            "amount" to BigDecimal("30.00"),
            "currency" to "CNY",
            "result" to "SUCCESS",
            "occurredAt" to Instant.parse("2026-08-17T09:00:00Z"),
            "verificationMaterial" to "test-secret",
        )

        val missingAttempt = postJson("/api/channel/payment-results", baseCallback, expectedStatus = 200)
        assertThat(missingAttempt["accepted"].asBoolean()).isFalse()
        assertThat(missingAttempt["rejected"].asBoolean()).isTrue()
        assertThat(missingAttempt.requiredText("disposition")).isEqualTo("ATTEMPT_NOT_FOUND")
        assertThat(missingAttempt["attemptStatus"].isNull).isTrue()

        val currencyMismatch = postJson(
            "/api/channel/payment-results",
            baseCallback + mapOf(
                "notificationId" to "N-CURRENCY",
                "paymentAttemptId" to attemptId,
                "currency" to "USD",
            ),
            expectedStatus = 200,
        )
        assertThat(currencyMismatch["accepted"].asBoolean()).isFalse()
        assertThat(currencyMismatch.requiredText("rejectionSummary")).contains("does not match payment currency")

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(payment["notificationReceiveCount"].asInt()).isEqualTo(2)
        assertThat(payment.requiredText("lastNotificationIdentity")).isEqualTo("N-CURRENCY")
        assertThat(payment.requiredText("lastRejectionSummary")).contains("does not match payment currency")
        val receipts = payment["attempts"][0]["notificationReceipts"]
        assertThat(receipts).hasSize(1)
        assertThat(receipts[0].requiredText("notificationIdentity")).isEqualTo("N-CURRENCY")
        assertThat(receipts[0].requiredText("decision")).isEqualTo("REJECTED")
        assertThat(receipts[0].requiredText("rejectionSummary")).contains("does not match payment currency")
    }

    @Test
    fun `gateway exceptions leave a failed attempt with durable diagnostics`() {
        jdbcTemplate.update(
            "update merchant_channel_configuration set channel_id = ? where merchant_id = ? and channel_id = ?",
            "C-THROW",
            "M-001",
            "C-001",
        )
        try {
            val created = postJson(
                "/api/payments",
                paymentRequest("O-GATEWAY-ERROR", "K-GATEWAY-ERROR", "20.00"),
                expectedStatus = 201,
            )
            val paymentId = created.requiredText("paymentId")
            val failedAttempt = postJson(
                "/api/payments/$paymentId/attempts",
                emptyMap<String, Any>(),
                expectedStatus = 200,
            )

            assertThat(failedAttempt.requiredText("channelId")).isEqualTo("C-THROW")
            assertThat(failedAttempt.requiredText("paymentStatus")).isEqualTo("FAILED")
            assertThat(failedAttempt.requiredText("attemptStatus")).isEqualTo("FAILED")

            val payment = getJson("/api/payments/$paymentId")
            assertThat(payment.requiredText("status")).isEqualTo("FAILED")
            assertThat(payment["attempts"]).hasSize(1)
            assertThat(payment["attempts"][0].requiredText("finalResult")).isEqualTo("GATEWAY_REJECTED")
            assertThat(payment["attempts"][0].requiredText("rejectionSummary")).contains("CHANNEL_GATEWAY_ERROR")
        } finally {
            jdbcTemplate.update(
                "update merchant_channel_configuration set channel_id = ? where merchant_id = ? and channel_id = ?",
                "C-001",
                "M-001",
                "C-THROW",
            )
        }
    }

    @Test
    fun `optimistic version prevents concurrent callbacks from silently overwriting one another`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-CONCURRENT", "K-CONCURRENT", "45.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        ).requiredText("paymentAttemptId")

        val ready = CountDownLatch(2)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf("A", "B").map { suffix ->
                executor.submit {
                    try {
                        TransactionTemplate(transactionManager).executeWithoutResult {
                            val payment = requireNotNull(
                                entityManager.find(Payment::class.java, PaymentId.parse(paymentId))
                            )
                            val attempt = payment.attempts.single { it.id == PaymentAttemptId.parse(attemptId) }
                            ready.countDown()
                            check(ready.await(5, TimeUnit.SECONDS)) { "concurrent callback workers did not rendezvous" }
                            payment.recordChannelResult(
                                paymentAttemptId = attempt.id,
                                channelId = "C-001",
                                notificationId = "N-CONCURRENT-$suffix",
                                channelTransactionId = "CT-CONCURRENT-$suffix",
                                amount = BigDecimal("45.00"),
                                currency = "CNY",
                                result = "SUCCESS",
                                occurredAt = LocalDateTime.parse("2026-08-17T10:00:00"),
                                receivedAt = LocalDateTime.parse("2026-08-17T10:00:01"),
                                verified = true,
                                verificationSummary = "concurrency test verified",
                            )
                            entityManager.flush()
                        }
                    } catch (error: Throwable) {
                        failures += error
                    }
                }
            }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertThat(failures).hasSize(1)
        assertThat(failures.single().causalChain().any {
            it is OptimisticLockException || it is OptimisticLockingFailureException
        }).isTrue()
        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(payment["notificationReceiveCount"].asInt()).isEqualTo(1)
        assertThat(payment["merchantSuccessNotificationIntentCount"].asInt()).isEqualTo(1)
    }

    private fun paymentRequest(
        merchantOrderNumber: String,
        idempotencyKey: String,
        amount: String,
    ): Map<String, Any> = mapOf(
        "merchantId" to "M-001",
        "merchantOrderNumber" to merchantOrderNumber,
        "idempotencyKey" to idempotencyKey,
        "amount" to BigDecimal(amount),
        "currency" to "CNY",
        "paymentMethod" to "CARD",
        "expiresAt" to Instant.parse("2030-01-01T00:00:00Z"),
    )

    private fun postJson(path: String, payload: Any, expectedStatus: Int): JsonNode {
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload))
        )
            .andExpect(status().`is`(expectedStatus))
            .andReturn()
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun getJson(path: String): JsonNode {
        val result = mockMvc.perform(get(path))
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun JsonNode.requiredText(field: String): String =
        requireNotNull(get(field)) { "missing JSON field $field in $this" }.asText()

    private fun Throwable.causalChain(): Sequence<Throwable> =
        generateSequence(this) { it.cause }
}
