package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.transfer.StartSettlementTransferHandler
import com.only4.cap4k.reference.payment.adapter.start.PaymentExpiryScheduler
import com.only4.cap4k.reference.payment.adapter.start.RefundReviewScheduler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.lifecycle.ActivateMerchantSettlementCmd
import com.only4.cap4k.reference.payment.application.subscribers.domain.merchant_settlement.MerchantSettlementCompletedDomainEventSubscriber
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.SettlementFeeRule
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.adjudicateReview
import com.only4.cap4k.reference.payment.domain.aggregates.payment.reserveRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewDecisionType
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewEligibilityImpact
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
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
    @param:Autowired private val refundReviewScheduler: RefundReviewScheduler,
    @param:Autowired private val paymentExpiryScheduler: PaymentExpiryScheduler,
) {

    @field:MockitoSpyBean
    private lateinit var transferHandler: StartSettlementTransferHandler

    @field:MockitoSpyBean
    private lateinit var activationHandler: ActivateMerchantSettlementCmd.Handler

    @field:MockitoSpyBean
    private lateinit var completedSubscriber: MerchantSettlementCompletedDomainEventSubscriber

    @BeforeEach
    fun resetReferenceClock() {
        val response = performRawPost(
            "/api/reference-fixtures/clock/set",
            mapOf("instant" to Instant.parse("2026-08-22T12:00:00Z")),
        )
        assertThat(response.status).withFailMessage(response.body.toString()).isEqualTo(200)
        val channel = performRawPost(
            "/api/reference-fixtures/payment-channel-script/reset",
            mapOf("channelId" to "C-001"),
        )
        assertThat(channel.status).withFailMessage(channel.body.toString()).isEqualTo(200)
    }

    @Test
    @DisplayName("PAY-AC-001..006/013/016 — 支付主链、幂等与 HTTP/JPA 回读")
    fun `create attempt confirm duplicate conflict and query form one durable payment chain`() {
        // Arrange：准备一个明确的 merchant order、idempotency key、金额和 CNY 渠道前提。
        val createRequest = paymentRequest(
            merchantOrderNumber = "O-001",
            idempotencyKey = "K-001",
            amount = "100.00",
        )

        // Act：所有动作都经真实 HTTP binding → Command/UoW → H2 持久化入口完成。
        val created = postJson("/api/payments", createRequest, expectedStatus = 201)
        val paymentId = created.requiredText("paymentId")
        // Assert：先看 API 状态/幂等语义，再通过 GET 和数据库回读确认 durable facts。
        assertThat(created.requiredText("status")).isEqualTo("PAYABLE")
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
        assertThat(beforeAttempt["money"].requiredText("amountMinor")).isEqualTo("10000")
        assertThat(beforeAttempt["money"].requiredText("currency")).isEqualTo("CNY")
        assertThat(beforeAttempt.requiredText("status")).isEqualTo("PAYABLE")
        assertThat(beforeAttempt["attemptCount"].asInt()).isZero()

        val attempt = postJson("/api/payments/$paymentId/attempts", emptyMap<String, Any>(), expectedStatus = 200)
        val attemptId = attempt.requiredText("paymentAttemptId")
        assertThat(attempt.requiredText("channelId")).isEqualTo("C-001")
        assertThat(attempt.requiredText("paymentStatus")).isEqualTo("PROCESSING")
        assertThat(attempt.requiredText("attemptStatus")).isEqualTo("ACCEPTED")

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
        assertThat(untrusted.requiredText("disposition")).isEqualTo("REJECTED_INVALID")
        assertThat(untrusted.requiredText("paymentStatus")).isEqualTo("PROCESSING")
        assertThat(untrusted.requiredText("rejectionSummary"))
            .containsAnyOf("核验失败", "reference callback evidence")

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
        assertThat(amountMismatch.requiredText("rejectionSummary")).contains("与支付金额")

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
        assertThat(accepted.requiredText("disposition")).isEqualTo("ACCEPTED")
        assertThat(accepted["successFactFormedNow"].asBoolean()).isTrue()
        assertThat(accepted.requiredText("paymentStatus")).isEqualTo("SUCCEEDED")
        assertThat(accepted.requiredText("attemptStatus")).isEqualTo("SUCCEEDED")
        val paymentCallbackOperationId = accepted["receipt"].requiredText("operationId")
        assertThat(accepted["receipt"].requiredText("commandType")).isEqualTo("ReceivePaymentChannelResult")
        assertThat(accepted["receipt"].requiredText("acceptanceStatus")).isEqualTo("ACCEPTED")
        assertThat(accepted["receipt"]["resource"].requiredText("resourceType")).isEqualTo("ChannelResultReceipt")
        assertThat(accepted["receipt"]["resource"].requiredText("resourceId")).isNotBlank()
        assertThat(accepted["receipt"]["readAfter"].requiredText("resourceUrl"))
            .isEqualTo("/api/channel/payment-results/N-003/receipts?channelId=C-001")
        val paymentCallbackOperation = getJson("/api/operations/$paymentCallbackOperationId")["operation"]
        assertThat(paymentCallbackOperation.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(paymentCallbackOperation.requiredText("finality")).isEqualTo("FINAL")
        assertThat(paymentCallbackOperation["resource"]).isEqualTo(accepted["receipt"]["resource"])

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
        assertThat(requireNotNull(duplicate).requiredText("disposition")).isEqualTo("DUPLICATE")
        assertThat(requireNotNull(duplicate)["successFactFormedNow"].asBoolean()).isFalse()
        assertThat(requireNotNull(duplicate)["notificationReceiveCount"].asInt()).isEqualTo(6)
        assertThat(requireNotNull(duplicate)["receipt"].requiredText("operationId"))
            .isEqualTo(paymentCallbackOperationId)
        assertThat(requireNotNull(duplicate)["receipt"].requiredText("acceptanceStatus"))
            .isEqualTo("ALREADY_ACCEPTED")
        assertThat(requireNotNull(duplicate)["receipt"]["idempotentReplay"].asBoolean()).isTrue()

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
        assertThat(conflictingFailure.requiredText("disposition")).isEqualTo("CONFLICTING")
        assertThat(conflictingFailure.requiredText("paymentStatus")).isEqualTo("SUCCEEDED")
        assertThat(conflictingFailure.requiredText("conflictSummary")).contains("支付成功事实形成后")

        val forbiddenAttempt = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 409,
        )
        assertThat(forbiddenAttempt.requiredText("code")).isEqualTo("PAYMENT_STATE_CONFLICT")

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(payment["money"].requiredText("amountMinor")).isEqualTo("10000")
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
        assertThat(payment.requiredText("expiresAt")).isEqualTo("2026-08-22T12:30:00Z")
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
        assertThat(persistedAttempt["verifiedNotificationCount"].asInt()).isEqualTo(2)
        assertThat(persistedAttempt["rejectedNotificationCount"].asInt()).isEqualTo(2)
        assertThat(persistedAttempt["conflictingNotificationCount"].asInt()).isEqualTo(1)
        assertThat(persistedAttempt.requiredText("notificationFirstReceivedAt")).isNotBlank()
        assertThat(persistedAttempt.requiredText("notificationLastReceivedAt")).isNotBlank()
        assertThat(persistedAttempt["notificationReceipts"]).hasSize(4)
        val acceptedReceipt = persistedAttempt["notificationReceipts"].first { it.requiredText("notificationIdentity") == "N-003" }
        assertThat(acceptedReceipt["receiveCount"].asInt()).isEqualTo(4)
        assertThat(acceptedReceipt["verified"].asBoolean()).isTrue()
        assertThat(acceptedReceipt["accepted"].asBoolean()).isTrue()
        assertThat(acceptedReceipt.requiredText("decision")).isEqualTo("ACCEPTED")

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
        assertThat(storedDecision).isEqualTo(ChannelResultDisposition.SUCCESS_ACCEPTED.value)

        TransactionTemplate(transactionManager).executeWithoutResult {
            entityManager.clear()
            val reloaded = requireNotNull(entityManager.find(Payment::class.java, PaymentId.parse(paymentId)))
            val receipt = reloaded.attempts.single()
                .paymentNotificationReceipts
                .single { it.notificationIdentity == "N-003" }

            assertThat(receipt.decision).isEqualTo(ChannelResultDisposition.SUCCESS_ACCEPTED)
            assertThat(receipt.decision.group).isEqualTo("accepted")
            assertThat(receipt.decision.terminal).isTrue()
        }
    }

    @Test
    @DisplayName("PAY-AC-007 — 到期关闭与 scheduler 幂等")
    fun `payment expiry closes without pending attempts and repeated scans stay idempotent`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-EXPIRY-CLOSE", "K-EXPIRY-CLOSE", "18.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        jdbcTemplate.update(
            "update payment set expires_at = ? where id = ?",
            LocalDateTime.parse("2026-08-21T00:00:00"),
            paymentId,
        )

        paymentExpiryScheduler.expirePayments()
        paymentExpiryScheduler.expirePayments()

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("CLOSED")
        assertThat(payment.requiredText("closedAt")).isNotBlank()
        assertThat(payment.requiredText("closeReason")).isEqualTo("PAYMENT_EXPIRED_WITHOUT_PENDING_ATTEMPT")
        assertThat(payment["reviews"]).isEmpty()
        assertThat(payment["settlementEligible"].asBoolean()).isFalse()

        val attempt = postJson("/api/payments/$paymentId/attempts", emptyMap<String, Any>(), expectedStatus = 409)
        assertThat(attempt.requiredText("code")).isEqualTo("PAYMENT_STATE_CONFLICT")
    }

    @Test
    @DisplayName("PAY-AC-008 — UNKNOWN 复核到可信结果收敛")
    fun `expired processing payment enters one stable review and trustworthy success resolves it`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-EXPIRY-UNKNOWN", "K-EXPIRY-UNKNOWN", "27.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        ).requiredText("paymentAttemptId")
        jdbcTemplate.update(
            "update payment set expires_at = ? where id = ?",
            LocalDateTime.parse("2026-08-21T00:00:00"),
            paymentId,
        )

        paymentExpiryScheduler.expirePayments()
        paymentExpiryScheduler.expirePayments()

        val unknown = getJson("/api/payments/$paymentId")
        assertThat(unknown.requiredText("status")).isEqualTo("RESULT_UNKNOWN")
        assertThat(unknown["attempts"][0].requiredText("status")).isEqualTo("RESULT_UNKNOWN")
        assertThat(unknown["reviews"]).hasSize(1)
        assertThat(unknown["reviews"][0].requiredText("type")).isEqualTo("EXPIRY_RESULT_UNKNOWN")
        assertThat(unknown["reviews"][0].requiredText("status")).isEqualTo("OPEN")
        assertThat(unknown["settlementEligible"].asBoolean()).isFalse()

        val accepted = postJson(
            "/api/channel/payment-results",
            paymentCallback(paymentId, attemptId, "N-EXPIRY-UNKNOWN", "CT-EXPIRY-UNKNOWN", "27.00", "SUCCESS"),
            expectedStatus = 200,
        )
        assertThat(accepted.requiredText("disposition")).isEqualTo("ACCEPTED")
        assertThat(accepted["settlementEligible"].asBoolean()).isTrue()

        val resolved = getJson("/api/payments/$paymentId")
        assertThat(resolved.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(resolved["reviews"][0].requiredText("status")).isEqualTo("RESOLVED")
        assertThat(resolved["reviews"][0]["decisions"]).hasSize(1)
        assertThat(resolved["reviews"][0]["decisions"][0].requiredText("decision")).isEqualTo("SYSTEM_ACCEPT_SUCCESS")
        assertThat(resolved.requiredText("merchantSuccessNotificationIntentState")).isEqualTo("READY")
    }

    @Test
    @DisplayName("PAY-AC-008/015 — scheduler/callback 并发收敛")
    fun `scheduler and callback race converges without losing success evidence`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-EXPIRY-CALLBACK-RACE", "K-EXPIRY-CALLBACK-RACE", "31.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        ).requiredText("paymentAttemptId")
        jdbcTemplate.update(
            "update payment set expires_at = ? where id = ?",
            LocalDateTime.parse("2026-08-21T00:00:00"),
            paymentId,
        )

        val callbackLoaded = CountDownLatch(1)
        val expiryCommitted = CountDownLatch(1)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val callback = executor.submit {
                try {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        val payment = requireNotNull(entityManager.find(Payment::class.java, PaymentId.parse(paymentId)))
                        val attempt = payment.attempts.single { it.id == PaymentAttemptId.parse(attemptId) }
                        callbackLoaded.countDown()
                        check(expiryCommitted.await(5, TimeUnit.SECONDS)) { "expiry command did not finish" }
                        payment.recordChannelResult(
                            paymentAttemptId = attempt.id,
                            channelId = "C-001",
                            notificationId = "N-EXPIRY-CALLBACK-RACE",
                            channelTransactionId = "CT-EXPIRY-CALLBACK-RACE",
                            amount = BigDecimal("31.00"),
                            currency = "CNY",
                            result = "SUCCESS",
                            occurredAt = LocalDateTime.parse("2026-08-22T00:30:00"),
                            receivedAt = LocalDateTime.parse("2026-08-22T00:30:01"),
                            verified = true,
                            verificationSummary = "scheduler callback race verified",
                            settlementFeeRule = SettlementFeeRule(
                                configurationId = attempt.channelConfigurationId,
                                basisPoints = 200,
                                fixedFeeAmount = BigDecimal.ZERO,
                                roundingMode = RoundingMode.HALF_UP,
                                currencyPrecision = 2,
                            ),
                        )
                        entityManager.flush()
                    }
                } catch (error: Throwable) {
                    failures += error
                }
            }
            val expiry = executor.submit {
                try {
                    check(callbackLoaded.await(5, TimeUnit.SECONDS)) { "callback transaction did not load the payment" }
                    paymentExpiryScheduler.expirePayments()
                } catch (error: Throwable) {
                    failures += error
                } finally {
                    expiryCommitted.countDown()
                }
            }
            expiry.get(10, TimeUnit.SECONDS)
            callback.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertThat(failures).hasSize(1)
        val failureChain = failures.single().causalChain().toList()
        assertThat(failureChain.any {
            it is OptimisticLockException || it is OptimisticLockingFailureException
        })
            .withFailMessage("unexpected concurrency failure chain: %s", failureChain.map { "${it::class.qualifiedName}: ${it.message}" })
            .isTrue()

        paymentExpiryScheduler.expirePayments()
        val convergedCallback = postJson(
            "/api/channel/payment-results",
            paymentCallback(
                paymentId,
                attemptId,
                "N-EXPIRY-CALLBACK-RACE",
                "CT-EXPIRY-CALLBACK-RACE",
                "31.00",
                "SUCCESS",
            ),
            expectedStatus = 200,
        )
        assertThat(convergedCallback.requiredText("disposition"))
            .isIn("ACCEPTED", "DUPLICATE")

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(payment["successFactFormed"].asBoolean()).isTrue()
        assertThat(payment["merchantSuccessNotificationIntentCount"].asInt()).isEqualTo(1)
        assertThat(payment["attempts"][0]["notificationReceipts"]).hasSize(1)
        assertThat(payment["reviews"].all { it.requiredText("status") == "RESOLVED" }).isTrue()
        assertThat(payment["settlementEligible"].asBoolean()).isTrue()
    }

    @Test
    @DisplayName("PAY-AC-009 — 迟到成功与人工复核")
    fun `late success after closed payment preserves terminal evidence until authorized review`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-LATE-CLOSED", "K-LATE-CLOSED", "36.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        postJson(
            "/api/reference-fixtures/payment-channel-script",
            mapOf("channelId" to "C-001", "script" to "REJECT_ON_SUBMIT"),
            expectedStatus = 200,
        )
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        ).requiredText("paymentAttemptId")
        jdbcTemplate.update(
            "update payment set expires_at = ? where id = ?",
            LocalDateTime.parse("2026-08-21T00:00:00"),
            paymentId,
        )
        paymentExpiryScheduler.expirePayments()

        val callback = postJson(
            "/api/channel/payment-results",
            paymentCallback(paymentId, attemptId, "N-LATE-CLOSED", "CT-LATE-CLOSED", "36.00", "SUCCESS"),
            expectedStatus = 200,
        )
        assertThat(callback.requiredText("paymentStatus")).isEqualTo("CLOSED")
        assertThat(callback.requiredText("disposition")).isEqualTo("LATE")
        assertThat(callback["conflicting"].asBoolean()).isTrue()
        assertThat(callback["settlementEligible"].asBoolean()).isFalse()
        assertThat(callback.requiredText("notificationIntentState")).isEqualTo("HELD_FOR_REVIEW")
        val reviewIdentity = callback.requiredText("reviewIdentity")

        val beforeDecision = getJson("/api/payments/$paymentId")
        assertThat(beforeDecision.requiredText("status")).isEqualTo("CLOSED")
        assertThat(beforeDecision["successFactFormed"].asBoolean()).isFalse()
        assertThat(beforeDecision["attempts"][0].requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(beforeDecision["attempts"][0]["notificationReceipts"][0]["accepted"].asBoolean()).isTrue()
        assertThat(beforeDecision["reviews"][0].requiredText("type")).isEqualTo("LATE_SUCCESS_AFTER_TERMINAL")

        val decisionRequest = mapOf(
            "paymentId" to paymentId,
            "merchantId" to "M-001",
            "idempotencyKey" to "decision-late-closed-keep",
            "reviewId" to reviewIdentity,
            "decisionIdentity" to "decision-late-closed-keep",
            "decision" to "KEEP_CURRENT_TERMINAL",
            "operatorIdentity" to "finance-reviewer-1",
            "operatorRole" to "PAYMENT_REVIEW_OPERATOR",
            "authorizationMaterial" to "AUTHORIZED",
            "reason" to "merchant order was already replaced",
            "evidence" to "ticket://late-closed/1",
            "decidedAt" to Instant.parse("2026-08-22T01:00:00Z"),
            "eligibilityImpact" to "ALLOW_SETTLEMENT",
            "remediationReference" to null,
        )
        val unauthorized = postJson(
            "/api/payments/$paymentId/reviews/$reviewIdentity/decisions",
            decisionRequest,
            expectedStatus = 400,
        )
        assertThat(unauthorized.requiredText("code")).isEqualTo("VALIDATION_ERROR")
        assertThat(getJson("/api/payments/$paymentId")["reviews"][0]["decisions"]).isEmpty()

        val decision = postJsonAsActor(
            "/api/payments/$paymentId/reviews/$reviewIdentity/decisions",
            decisionRequest,
            expectedStatus = 200,
        )
        assertThat(decision.requiredText("paymentStatus")).isEqualTo("CLOSED")
        assertThat(decision.requiredText("reviewStatus")).isEqualTo("RESOLVED")
        assertThat(decision["settlementEligible"].asBoolean()).isFalse()
        assertThat(decision.requiredText("notificationIntentState")).isEqualTo("CANCELLED")

        val replay = postJsonAsActor(
            "/api/payments/$paymentId/reviews/$reviewIdentity/decisions",
            decisionRequest,
            expectedStatus = 200,
        )
        assertThat(replay["decisionCount"].asInt()).isEqualTo(1)
        val changedReplay = postJsonAsActor(
            "/api/payments/$paymentId/reviews/$reviewIdentity/decisions",
            decisionRequest + ("reason" to "changed reason must not reuse the decision identity"),
            expectedStatus = 409,
        )
        assertThat(changedReplay.requiredText("code")).isEqualTo("IDEMPOTENCY_CONFLICT")

        val afterDecision = getJson("/api/payments/$paymentId")
        assertThat(afterDecision["reviews"][0]["decisions"]).hasSize(1)
        assertThat(afterDecision["attempts"][0]["notificationReceipts"]).hasSize(1)
    }

    @Test
    @DisplayName("PAY-AC-015 — 复核裁决与迟到冲突证据")
    fun `review and callback race preserves the authorized decision and later conflict evidence`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-REVIEW-CALLBACK-RACE", "K-REVIEW-CALLBACK-RACE", "39.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        postJson(
            "/api/reference-fixtures/payment-channel-script",
            mapOf("channelId" to "C-001", "script" to "REJECT_ON_SUBMIT"),
            expectedStatus = 200,
        )
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        ).requiredText("paymentAttemptId")
        jdbcTemplate.update(
            "update payment set expires_at = ? where id = ?",
            LocalDateTime.parse("2026-08-21T00:00:00"),
            paymentId,
        )
        paymentExpiryScheduler.expirePayments()
        val firstLate = postJson(
            "/api/channel/payment-results",
            paymentCallback(
                paymentId,
                attemptId,
                "N-REVIEW-CALLBACK-RACE-1",
                "CT-REVIEW-CALLBACK-RACE-1",
                "39.00",
                "SUCCESS",
            ),
            expectedStatus = 200,
        )
        val reviewIdentity = firstLate.requiredText("reviewIdentity")

        val ready = CountDownLatch(2)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val review = executor.submit {
                try {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        val payment = requireNotNull(entityManager.find(Payment::class.java, PaymentId.parse(paymentId)))
                        ready.countDown()
                        check(ready.await(5, TimeUnit.SECONDS)) { "review and callback workers did not rendezvous" }
                        payment.adjudicateReview(
                            reviewIdentity = reviewIdentity,
                            decisionIdentity = "decision-review-callback-race",
                            decision = PaymentReviewDecisionType.KEEP_CURRENT_TERMINAL,
                            operatorIdentity = "finance-reviewer-race",
                            operatorRole = "PAYMENT_REVIEW_OPERATOR",
                            authorized = true,
                            reason = "retain closed payment after replacement",
                            evidence = "ticket://review-callback-race/1",
                            decidedAt = LocalDateTime.parse("2026-08-22T02:00:00"),
                            eligibilityImpact = PaymentReviewEligibilityImpact.ALLOW_SETTLEMENT,
                            remediationReference = null,
                        )
                        entityManager.flush()
                    }
                } catch (error: Throwable) {
                    failures += error
                }
            }
            val callback = executor.submit {
                try {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        val payment = requireNotNull(entityManager.find(Payment::class.java, PaymentId.parse(paymentId)))
                        val attempt = payment.attempts.single { it.id == PaymentAttemptId.parse(attemptId) }
                        ready.countDown()
                        check(ready.await(5, TimeUnit.SECONDS)) { "review and callback workers did not rendezvous" }
                        payment.recordChannelResult(
                            paymentAttemptId = attempt.id,
                            channelId = "C-001",
                            notificationId = "N-REVIEW-CALLBACK-RACE-2",
                            channelTransactionId = "CT-REVIEW-CALLBACK-RACE-2",
                            amount = BigDecimal("39.00"),
                            currency = "CNY",
                            result = "SUCCESS",
                            occurredAt = LocalDateTime.parse("2026-08-22T02:01:00"),
                            receivedAt = LocalDateTime.parse("2026-08-22T02:01:01"),
                            verified = true,
                            verificationSummary = "review callback race verified",
                            settlementFeeRule = SettlementFeeRule(
                                configurationId = attempt.channelConfigurationId,
                                basisPoints = 200,
                                fixedFeeAmount = BigDecimal.ZERO,
                                roundingMode = RoundingMode.HALF_UP,
                                currencyPrecision = 2,
                            ),
                        )
                        entityManager.flush()
                    }
                } catch (error: Throwable) {
                    failures += error
                }
            }
            review.get(10, TimeUnit.SECONDS)
            callback.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        assertThat(failures).hasSize(1)
        val reviewRaceFailureChain = failures.single().causalChain().toList()
        assertThat(reviewRaceFailureChain.any {
            it is OptimisticLockException || it is OptimisticLockingFailureException ||
                it.javaClass.simpleName == "StaleObjectStateException"
        }).withFailMessage(
            "unexpected review/callback race failure chain: %s",
            reviewRaceFailureChain.map { "${it::class.qualifiedName}: ${it.message}" },
        ).isTrue()

        val decisionRequest = mapOf(
            "paymentId" to paymentId,
            "merchantId" to "M-001",
            "idempotencyKey" to "decision-review-callback-race-http",
            "reviewId" to reviewIdentity,
            "decisionIdentity" to "decision-review-callback-race-http",
            "decision" to "KEEP_CURRENT_TERMINAL",
            "operatorIdentity" to "finance-reviewer-race",
            "operatorRole" to "PAYMENT_REVIEW_OPERATOR",
            "authorizationMaterial" to "AUTHORIZED",
            "reason" to "retain closed payment after replacement",
            "evidence" to "ticket://review-callback-race/1",
            "decidedAt" to Instant.parse("2026-08-22T02:00:00Z"),
            "eligibilityImpact" to "ALLOW_SETTLEMENT",
            "remediationReference" to null,
        )
        val paymentAfterRace = getJson("/api/payments/$paymentId")
        val racedCallbackWasCommitted = paymentAfterRace["attempts"][0]["notificationReceipts"]
            .any { it.requiredText("notificationIdentity") == "N-REVIEW-CALLBACK-RACE-2" }
        val reviewAfterRace = paymentAfterRace["reviews"]
            .first { it.requiredText("reviewIdentity") == reviewIdentity }
        if (reviewAfterRace["decisions"].isEmpty) {
            postJsonAsActor(
                "/api/payments/$paymentId/reviews/$reviewIdentity/decisions",
                decisionRequest,
                expectedStatus = 200,
            )
        }
        val secondLate = postJson(
            "/api/channel/payment-results",
            paymentCallback(
                paymentId,
                attemptId,
                "N-REVIEW-CALLBACK-RACE-2",
                "CT-REVIEW-CALLBACK-RACE-2",
                "39.00",
                "SUCCESS",
            ) + ("occurredAt" to Instant.parse("2026-08-22T02:01:00Z")),
            expectedStatus = 200,
        )
        assertThat(secondLate.requiredText("disposition")).isEqualTo(
            if (racedCallbackWasCommitted) "DUPLICATE" else "LATE",
        )
        assertThat(secondLate["conflicting"].asBoolean()).isTrue()

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("CLOSED")
        assertThat(payment["attempts"][0]["notificationReceipts"]).hasSize(2)
        assertThat(payment["reviews"]).hasSize(2)
        assertThat(payment["reviews"].count { it.requiredText("status") == "RESOLVED" }).isEqualTo(1)
        assertThat(payment["reviews"].count { it.requiredText("status") == "OPEN" }).isEqualTo(1)
        assertThat(payment["settlementEligible"].asBoolean()).isFalse()
    }

    @Test
    @DisplayName("PAY-AC-010 — 双尝试成功的一次性成功事实")
    fun `second attempt success preserves both successes while revenue and intent remain once only`() {
        val created = postJson(
            "/api/payments",
            paymentRequest("O-DOUBLE-SUCCESS", "K-DOUBLE-SUCCESS", "42.00"),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        val firstAttemptId = postJson(
            "/api/payments/$paymentId/attempts",
            mapOf("idempotencyKey" to "double-attempt-create-1"),
            expectedStatus = 201,
        ).requiredText("paymentAttemptId")
        val secondAttemptId = postJson(
            "/api/payments/$paymentId/attempts",
            mapOf(
                "idempotencyKey" to "double-attempt-create-2",
                "riskReason" to "explicit concurrent-attempt acceptance test",
            ),
            expectedStatus = 201,
        ).requiredText("paymentAttemptId")
        postJson(
            "/api/payments/$paymentId/attempts/$firstAttemptId/submissions",
            mapOf("idempotencyKey" to "double-attempt-submit-1"),
            expectedStatus = 200,
        )
        postJson(
            "/api/payments/$paymentId/attempts/$secondAttemptId/submissions",
            mapOf("idempotencyKey" to "double-attempt-submit-2"),
            expectedStatus = 200,
        )

        val firstAccepted = postJson(
            "/api/channel/payment-results",
            paymentCallback(paymentId, firstAttemptId, "N-DOUBLE-1", "CT-DOUBLE-1", "42.00", "SUCCESS"),
            expectedStatus = 200,
        )
        assertThat(firstAccepted.requiredText("disposition")).isEqualTo("ACCEPTED")
        val secondEvidence = postJson(
            "/api/channel/payment-results",
            paymentCallback(paymentId, secondAttemptId, "N-DOUBLE-2", "CT-DOUBLE-2", "42.00", "SUCCESS"),
            expectedStatus = 200,
        )
        assertThat(secondEvidence.requiredText("disposition")).isEqualTo("CONFLICTING")
        assertThat(secondEvidence["conflicting"].asBoolean()).isTrue()
        assertThat(secondEvidence["settlementEligible"].asBoolean()).isFalse()

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(payment["attempts"]).hasSize(2)
        assertThat(payment["attempts"].count { it.requiredText("status") == "SUCCEEDED" }).isEqualTo(2)
        assertThat(payment["merchantSuccessNotificationIntentCount"].asInt()).isEqualTo(1)
        assertThat(payment.requiredText("merchantSuccessNotificationIntentState")).isEqualTo("HELD_FOR_REVIEW")
        assertThat(payment.requiredText("channelTransactionId")).isEqualTo("CT-DOUBLE-1")
        assertThat(payment["reviews"].map { it.requiredText("type") })
            .contains("CONCURRENT_ATTEMPT_RISK", "MULTIPLE_ATTEMPT_SUCCESS")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from payment where id = ? and settlement_fee_fact_identity is not null",
                Long::class.java,
                paymentId,
            )
        ).isEqualTo(1L)
    }

    @Test
    @DisplayName("PAY-AC-011 — 已成功订单不得重复收款")
    fun `a paid merchant order rejects a second executable payment`() {
        val firstPaymentId = postJson(
            "/api/payments",
            paymentRequest("O-ORDER-RACE", "K-ORDER-RACE-1", "55.00"),
            expectedStatus = 201,
        ).requiredText("paymentId")
        val firstAttemptId = postJson(
            "/api/payments/$firstPaymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        ).requiredText("paymentAttemptId")
        val firstResult = postJson(
            "/api/channel/payment-results",
            paymentCallback(firstPaymentId, firstAttemptId, "N-ORDER-RACE-1", "CT-ORDER-RACE-1", "55.00", "SUCCESS"),
            expectedStatus = 200,
        )
        assertThat(firstResult.requiredText("disposition")).isEqualTo("ACCEPTED")

        val rejectedCreate = postJson(
            "/api/payments",
            paymentRequest("O-ORDER-RACE", "K-ORDER-RACE-2", "55.00"),
            expectedStatus = 409,
        )
        assertThat(rejectedCreate.requiredText("code")).isIn("ORDER_ALREADY_PAID", "BUSINESS_CONFLICT")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from payment where merchant_id = ? and merchant_order_number = ?",
                Long::class.java,
                "M-001",
                "O-ORDER-RACE",
            )
        ).isEqualTo(1L)
    }

    @Test
    @DisplayName("PAY-AC-012 — 输入拒绝不占幂等键")
    fun `invalid amount precision and unsupported currency never reserve an idempotency key`() {
        val base = paymentRequest(
            merchantOrderNumber = "O-INVALID",
            idempotencyKey = "K-INVALID",
            amount = "10.00",
        )

        assertThat(
            postJson("/api/payments", base + ("amount" to BigDecimal.ZERO), expectedStatus = 400)
                .requiredText("code")
        ).isEqualTo("VALIDATION_ERROR")
        assertThat(
            postJson("/api/payments", base + ("amount" to BigDecimal("10.001")), expectedStatus = 400)
                .requiredText("code")
        ).isEqualTo("VALIDATION_ERROR")
        assertThat(
            postJson("/api/payments", base + ("currency" to "USD"), expectedStatus = 400)
                .requiredText("code")
        ).isEqualTo("VALIDATION_ERROR")

        val valid = postJson("/api/payments", base, expectedStatus = 201)
        assertThat(valid.requiredText("status")).isEqualTo("PAYABLE")
        assertThat(valid["idempotentReplay"].asBoolean()).isFalse()
    }


    @Test
    @DisplayName("PAY-AC-017 — 不匹配输入可查询且不推进状态")
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
        assertThat(missingAttempt.requiredText("disposition")).isEqualTo("UNKNOWN_REFERENCE")
        assertThat(missingAttempt["attemptStatus"].isNull).isTrue()
        val missingAttemptReceipt = getJson(
            "/api/channel/payment-results/N-MISSING-ATTEMPT/receipts?channelId=C-001",
        )
        assertThat(missingAttemptReceipt["totalReceiveCount"].asInt()).isEqualTo(1)
        assertThat(missingAttemptReceipt["receipts"]).hasSize(1)
        assertThat(missingAttemptReceipt["receipts"][0].requiredText("disposition"))
            .isEqualTo("UNKNOWN_REFERENCE")
        assertThat(missingAttemptReceipt["receipts"][0].requiredText("paymentAttemptId"))
            .isEqualTo("018f22a0-0000-7000-8000-000000000099")

        val unknownPaymentId = "018f22a0-0000-7000-8000-000000000098"
        val unknownPayment = postJson(
            "/api/channel/payment-results",
            baseCallback + mapOf(
                "notificationId" to "N-MISSING-PAYMENT",
                "paymentId" to unknownPaymentId,
                "paymentAttemptId" to "018f22a0-0000-7000-8000-000000000097",
                "channelTransactionId" to "CT-MISSING-PAYMENT",
            ),
            expectedStatus = 200,
        )
        assertThat(unknownPayment.requiredText("disposition")).isEqualTo("UNKNOWN_REFERENCE")
        assertThat(unknownPayment["receipt"]["resource"].requiredText("resourceType"))
            .isEqualTo("ChannelResultReceipt")
        val unknownPaymentReplay = postJson(
            "/api/channel/payment-results",
            baseCallback + mapOf(
                "notificationId" to "N-MISSING-PAYMENT",
                "paymentId" to unknownPaymentId,
                "paymentAttemptId" to "018f22a0-0000-7000-8000-000000000097",
                "channelTransactionId" to "CT-MISSING-PAYMENT",
            ),
            expectedStatus = 200,
        )
        assertThat(unknownPaymentReplay.requiredText("disposition")).isEqualTo("DUPLICATE")
        assertThat(unknownPaymentReplay["receipt"].requiredText("operationId"))
            .isEqualTo(unknownPayment["receipt"].requiredText("operationId"))
        assertThat(unknownPaymentReplay["receipt"].requiredText("acceptanceStatus"))
            .isEqualTo("ALREADY_ACCEPTED")
        val unknownPaymentReceipt = getJson(
            "/api/channel/payment-results/N-MISSING-PAYMENT/receipts?channelId=C-001",
        )
        assertThat(unknownPaymentReceipt["totalReceiveCount"].asInt()).isEqualTo(2)
        assertThat(unknownPaymentReceipt["receipts"][0].requiredText("paymentId")).isEqualTo(unknownPaymentId)
        assertThat(unknownPaymentReceipt["receipts"][0].requiredText("verification")).isEqualTo("VERIFIED")

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
        assertThat(currencyMismatch.requiredText("rejectionSummary")).contains("与支付币种")

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(payment["notificationReceiveCount"].asInt()).isEqualTo(2)
        assertThat(payment.requiredText("lastNotificationIdentity")).isEqualTo("N-CURRENCY")
        assertThat(payment.requiredText("lastRejectionSummary")).contains("与支付币种")
        val receipts = payment["attempts"][0]["notificationReceipts"]
        assertThat(receipts).hasSize(1)
        assertThat(receipts[0].requiredText("notificationIdentity")).isEqualTo("N-CURRENCY")
        assertThat(receipts[0].requiredText("decision")).isEqualTo("REJECTED_INVALID")
        assertThat(receipts[0].requiredText("rejectionSummary")).contains("与支付币种")
    }

    @Test
    fun `provider rejection diagnostics stay in logs while payment and refund expose controlled Chinese summaries`() {
        val refundablePaymentId = createSucceededPayment("PROVIDER-REJECT-REFUND", "50.00")
        jdbcTemplate.update(
            "update merchant_channel_configuration set channel_id = ? where merchant_id = ? and channel_id = ?",
            "C-REJECT",
            "M-001",
            "C-001",
        )
        try {
            val createdPayment = postJson(
                "/api/payments",
                paymentRequest("O-PROVIDER-REJECT", "K-PROVIDER-REJECT", "20.00"),
                expectedStatus = 201,
            )
            val paymentId = createdPayment.requiredText("paymentId")
            val rejectedAttempt = postJson(
                "/api/payments/$paymentId/attempts",
                emptyMap<String, Any>(),
                expectedStatus = 200,
            )
            assertThat(rejectedAttempt.requiredText("paymentStatus")).isEqualTo("PAYABLE")
            assertThat(rejectedAttempt.requiredText("attemptStatus")).isEqualTo("REJECTED")
            val rejectedPayment = getJson("/api/payments/$paymentId")
            assertThat(rejectedPayment["attempts"][0].requiredText("rejectionSummary"))
                .contains("支付渠道不支持当前请求")
                .doesNotContain("deterministic fake gateway")

            val rejectedRefund = postJson(
                "/api/refunds",
                refundRequest(
                    refundablePaymentId,
                    "R-PROVIDER-REJECT",
                    "20.00",
                    "2026-08-17T12:10:00Z",
                ),
                expectedStatus = 201,
            )
            assertThat(rejectedRefund.requiredText("diagnosticSummary"))
                .isEqualTo("退款渠道不支持当前请求")
                .doesNotContain("deterministic fake refund gateway")
                .doesNotContain("only accepts")
            val refund = getJson("/api/refunds/${rejectedRefund.requiredText("refundId")}")
            assertThat(refund["attempts"][0].requiredText("rejectionSummary"))
                .contains("UNSUPPORTED_CHANNEL")
                .contains("退款渠道不支持当前请求")
                .doesNotContain("deterministic fake refund gateway")
        } finally {
            jdbcTemplate.update(
                "update merchant_channel_configuration set channel_id = ? where merchant_id = ? and channel_id = ?",
                "C-001",
                "M-001",
                "C-REJECT",
            )
        }
    }
    @Test
    fun `unsupported payment channel leaves a rejected attempt with durable diagnostics`() {
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
            assertThat(failedAttempt.requiredText("paymentStatus")).isEqualTo("PAYABLE")
            assertThat(failedAttempt.requiredText("attemptStatus")).isEqualTo("REJECTED")

            val payment = getJson("/api/payments/$paymentId")
            assertThat(payment.requiredText("status")).isEqualTo("PAYABLE")
            assertThat(payment["attempts"]).hasSize(1)
            assertThat(payment["attempts"][0].requiredText("finalResult")).isEqualTo("GATEWAY_REJECTED")
            val rejectionSummary = payment["attempts"][0].requiredText("rejectionSummary")
            assertThat(rejectionSummary)
                .contains("支付渠道不支持当前请求")
                .doesNotContain("模拟的确定性渠道故障")
                .doesNotContain("RuntimeException")
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
                                settlementFeeRule = SettlementFeeRule(
                                    configurationId = attempt.channelConfigurationId,
                                    basisPoints = 200,
                                    fixedFeeAmount = BigDecimal.ZERO,
                                    roundingMode = RoundingMode.HALF_UP,
                                    currencyPrecision = 2,
                                ),
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


    @Test
    fun `refund accepted callback is idempotent and queryable`() {
        val paymentId = createSucceededPayment("REFUND-HAPPY", "80.00")
        val create = postJson("/api/refunds", mapOf("merchantId" to "M-001", "merchantRefundNumber" to "R-001", "paymentId" to paymentId, "amount" to BigDecimal("30.00"), "currency" to "CNY", "requestedAt" to Instant.parse("2026-08-17T11:00:00Z")), 201)
        val refundId = create.requiredText("refundId"); val attemptId = create.requiredText("refundAttemptId")
        assertThat(create.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(postJson("/api/refunds", mapOf("merchantId" to "M-001", "merchantRefundNumber" to "R-001", "paymentId" to paymentId, "amount" to BigDecimal("30.00"), "currency" to "CNY", "requestedAt" to Instant.parse("2026-08-17T11:00:00Z")), 201)["idempotentReplay"].asBoolean()).isTrue()
        val callback = mapOf("channelId" to "C-001", "notificationId" to "R-N-001", "refundId" to refundId, "refundAttemptId" to attemptId, "channelRefundId" to "fake-refund-${create.requiredText("requestIdentity")}", "amount" to BigDecimal("30.00"), "currency" to "CNY", "result" to "SUCCESS", "occurredAt" to Instant.parse("2026-08-17T11:05:00Z"), "verificationMaterial" to "test-secret")
        val accepted = postJson("/api/channel/refund-results", callback, 200)
        assertThat(accepted.requiredText("disposition")).isEqualTo("ACCEPTED")
        val refundCallbackOperationId = accepted["receipt"].requiredText("operationId")
        assertThat(accepted["receipt"].requiredText("commandType")).isEqualTo("ReceiveRefundChannelResult")
        assertThat(accepted["receipt"].requiredText("acceptanceStatus")).isEqualTo("ACCEPTED")
        assertThat(accepted["receipt"]["resource"].requiredText("resourceType")).isEqualTo("Refund")
        assertThat(accepted["receipt"]["resource"].requiredText("resourceId")).isEqualTo(refundId)
        val refundCallbackOperation = getJson("/api/operations/$refundCallbackOperationId")["operation"]
        assertThat(refundCallbackOperation.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(refundCallbackOperation.requiredText("finality")).isEqualTo("FINAL")
        val replay = postJson("/api/channel/refund-results", callback, 200)
        assertThat(replay.requiredText("disposition")).isEqualTo("DUPLICATE")
        assertThat(replay["receipt"].requiredText("operationId")).isEqualTo(refundCallbackOperationId)
        assertThat(replay["receipt"].requiredText("acceptanceStatus")).isEqualTo("ALREADY_ACCEPTED")
        assertThat(replay["receipt"]["idempotentReplay"].asBoolean()).isTrue()
        val mismatch = postJson("/api/channel/refund-results", callback + mapOf("notificationId" to "R-N-002", "amount" to BigDecimal("29.99")), 200)
        assertThat(mismatch.requiredText("disposition")).isEqualTo("CONFLICTING")
        val conflictingFailure = postJson(
            "/api/channel/refund-results",
            callback + mapOf("notificationId" to "R-N-003", "result" to "FAILED"),
            200,
        )
        assertThat(conflictingFailure.requiredText("disposition")).isEqualTo("CONFLICTING")
        assertThat(getJson("/api/refunds/$refundId").requiredText("status")).isEqualTo("SUCCEEDED")
    }

    @Test
    @DisplayName("PAY-AC-020 — 全额退款主链")
    fun `a successful payment can be refunded in full`() {
        val paymentId = createSucceededPayment("REFUND-FULL", "100.00")
        val refund = createRefund(paymentId, "R-FULL", "100.00", "2026-08-17T11:00:00Z")
        val result = confirmRefund(refund, "100.00", "R-FULL-N-1", "SUCCESS", "2026-08-17T11:05:00Z")

        assertThat(result.requiredText("disposition")).isEqualTo("ACCEPTED")
        assertThat(result.requiredText("refundStatus")).isEqualTo("SUCCEEDED")
        val persistedRefund = getJson("/api/refunds/${refund.requiredText("refundId")}")
        assertThat(persistedRefund.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(persistedRefund["reservationActive"].asBoolean()).isFalse()
        assertThat(persistedRefund["reservationConvertedToSuccess"].asBoolean()).isTrue()

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["succeededAmount"].requiredText("amountMinor")).isEqualTo("10000")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("0")
    }

    @Test
    @DisplayName("PAY-AC-026/029 — 退款幂等与成功后冲突")
    fun `merchant refund number replay rejects changed critical content without a second refund`() {
        val paymentId = createSucceededPayment("REFUND-IDEMPOTENCY", "80.00")
        val original = createRefund(paymentId, "R-IDEMPOTENT", "20.00", "2026-08-17T11:00:00Z")

        val conflict = postJson(
            "/api/refunds",
            refundRequest(paymentId, "R-IDEMPOTENT", "21.00", "2026-08-17T11:01:00Z"),
            409,
        )
        assertThat(conflict.requiredText("code")).isEqualTo("IDEMPOTENCY_CONFLICT")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refund where merchant_id = ? and merchant_refund_number = ?",
                Long::class.java,
                "M-001",
                "R-IDEMPOTENT",
            )
        ).isEqualTo(1L)
        val persisted = getJson("/api/refunds/${original.requiredText("refundId")}")
        assertThat(persisted["attempts"]).hasSize(1)
        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("2000")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("6000")
    }

    @Test
    @DisplayName("PAY-AC-027/028 — 退款资格与期限拒绝")
    fun `refund application rejects merchant currency and channel eligibility mismatches without reservation`() {
        val merchantMismatchPayment = createSucceededPayment("REFUND-MERCHANT-MISMATCH", "40.00")
        val merchantMismatch = postJson(
            "/api/refunds",
            refundRequest(merchantMismatchPayment, "R-MERCHANT-MISMATCH", "10.00", "2026-08-17T11:00:00Z") +
                ("merchantId" to "M-OTHER"),
            400,
        )
        assertThat(merchantMismatch.requiredText("code")).isEqualTo("VALIDATION_ERROR")
        assertThat(getJson("/api/payments/$merchantMismatchPayment")["refundBudget"]["reservedAmount"].requiredText("amountMinor"))
            .isEqualTo("0")

        val currencyMismatchPayment = createSucceededPayment("REFUND-CURRENCY-MISMATCH", "40.00")
        jdbcTemplate.update("update payment set currency = ? where id = ?", "USD", currencyMismatchPayment)
        val currencyMismatch = postJson(
            "/api/refunds",
            refundRequest(currencyMismatchPayment, "R-CURRENCY-MISMATCH", "10.00", "2026-08-17T11:00:00Z"),
            400,
        )
        assertThat(currencyMismatch.requiredText("code")).isEqualTo("VALIDATION_ERROR")
        assertThat(getJson("/api/payments/$currencyMismatchPayment")["refundBudget"]["reservedAmount"].requiredText("amountMinor"))
            .isEqualTo("0")

        val noChannelPayment = createSucceededPayment("REFUND-NO-CHANNEL", "40.00")
        jdbcTemplate.update(
            "update merchant_channel_configuration set status = 1 where merchant_id = ? and channel_id = ?",
            "M-001",
            "C-001",
        )
        try {
            val noChannel = postJson(
                "/api/refunds",
                refundRequest(noChannelPayment, "R-NO-CHANNEL", "10.00", "2026-08-17T11:00:00Z"),
                409,
            )
            assertThat(noChannel.requiredText("code")).isEqualTo("NO_ELIGIBLE_CHANNEL")
        } finally {
            jdbcTemplate.update(
                "update merchant_channel_configuration set status = 0 where merchant_id = ? and channel_id = ?",
                "M-001",
                "C-001",
            )
        }
        assertThat(getJson("/api/payments/$noChannelPayment")["refundBudget"]["reservedAmount"].requiredText("amountMinor"))
            .isEqualTo("1000")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refund where merchant_refund_number in (?, ?, ?)",
                Long::class.java,
                "R-MERCHANT-MISMATCH",
                "R-CURRENCY-MISMATCH",
                "R-NO-CHANNEL",
            )
        ).isEqualTo(1L)
    }
    @Test
    @DisplayName("PAY-AC-021 — 部分退款预算")
    fun `multiple partial refunds remain independently queryable and update payment budget`() {
        val paymentId = createSucceededPayment("REFUND-PARTIAL", "100.00")
        val first = createRefund(paymentId, "R-PARTIAL-1", "30.00", "2026-08-17T11:00:00Z")
        confirmRefund(first, "30.00", "R-PARTIAL-N-1", "SUCCESS", "2026-08-17T11:05:00Z")
        val second = createRefund(paymentId, "R-PARTIAL-2", "20.00", "2026-08-17T11:10:00Z")
        confirmRefund(second, "20.00", "R-PARTIAL-N-2", "SUCCESS", "2026-08-17T11:15:00Z")

        assertThat(getJson("/api/refunds/${first.requiredText("refundId")}").requiredText("status"))
            .isEqualTo("SUCCEEDED")
        assertThat(getJson("/api/refunds/${second.requiredText("refundId")}").requiredText("status"))
            .isEqualTo("SUCCEEDED")
        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["succeededAmount"].requiredText("amountMinor")).isEqualTo("5000")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("5000")
    }

    @Test
    @DisplayName("PAY-AC-022 — 超额退款拒绝")
    fun `refund beyond the exact remaining amount is rejected without a channel request`() {
        val paymentId = createSucceededPayment("REFUND-OVER", "100.00")
        val successful = createRefund(paymentId, "R-OVER-SUCCESS", "60.00", "2026-08-17T11:00:00Z")
        confirmRefund(successful, "60.00", "R-OVER-N-1", "SUCCESS", "2026-08-17T11:05:00Z")

        val overRefund = postJson(
            "/api/refunds",
            refundRequest(paymentId, "R-OVER-REJECTED", "50.00", "2026-08-17T11:10:00Z"),
            409,
        )
        assertThat(overRefund.requiredText("code")).isEqualTo("REFUND_BUDGET_EXCEEDED")
        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["succeededAmount"].requiredText("amountMinor")).isEqualTo("6000")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("4000")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refund where merchant_refund_number = ?",
                Long::class.java,
                "R-OVER-REJECTED",
            )
        ).isEqualTo(0L)
    }

    @Test
    @DisplayName("PAY-AC-024 — 失败释放预占")
    fun `trusted failed refund result releases its payment reservation`() {
        val paymentId = createSucceededPayment("REFUND-FAILURE", "100.00")
        val refund = createRefund(paymentId, "R-FAILURE", "40.00", "2026-08-17T11:00:00Z")
        assertThat(getJson("/api/payments/$paymentId")["refundBudget"]["reservedAmount"].requiredText("amountMinor"))
            .isEqualTo("4000")

        val failed = confirmRefund(refund, "40.00", "R-FAILURE-N-1", "FAILED", "2026-08-17T11:05:00Z")
        assertThat(failed.requiredText("disposition")).isEqualTo("ACCEPTED")
        assertThat(failed.requiredText("refundStatus")).isEqualTo("FAILED")
        assertThat(failed["reservationReleasedNow"].asBoolean()).isTrue()
        val persistedRefund = getJson("/api/refunds/${refund.requiredText("refundId")}")
        assertThat(persistedRefund.requiredText("status")).isEqualTo("FAILED")
        assertThat(persistedRefund["reservationActive"].asBoolean()).isFalse()
        assertThat(persistedRefund["reservationReleased"].asBoolean()).isTrue()
        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["succeededAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("10000")
    }
    @Test
    @DisplayName("PAY-AC-025 — 退款 UNKNOWN 保留预占并进入复核")
    fun `unknown refund result remains reserved and scheduled review marks it`() {
        val paymentId = createSucceededPayment("REFUND-UNKNOWN", "60.00")
        val refund = createRefund(paymentId, "R-UNKNOWN", "20.00", "2026-08-17T11:00:00Z")
        val unknown = confirmRefund(refund, "20.00", "R-UNKNOWN-N-1", "UNKNOWN", "2026-08-17T11:05:00Z")
        assertThat(unknown.requiredText("disposition")).isEqualTo("ACCEPTED")
        assertThat(unknown.requiredText("refundStatus")).isEqualTo("RESULT_UNKNOWN")
        assertThat(unknown.requiredText("finality")).isEqualTo("NON_FINAL")

        postJson(
            "/api/reference-fixtures/clock/set",
            mapOf("instant" to Instant.parse("2026-08-22T13:00:00Z")),
            expectedStatus = 200,
        )
        refundReviewScheduler.review()

        val persistedRefund = getJson("/api/refunds/${refund.requiredText("refundId")}")
        assertThat(persistedRefund.requiredText("status")).isEqualTo("RESULT_UNKNOWN")
        assertThat(persistedRefund.requiredText("finality")).isEqualTo("REVIEW_REQUIRED")
        assertThat(persistedRefund["reservationActive"].asBoolean()).isTrue()
        assertThat(getJson("/api/payments/$paymentId")["refundBudget"]["reservedAmount"].requiredText("amountMinor"))
            .isEqualTo("2000")
    }

    @Test
    @DisplayName("PAY-AC-027/028 — 非成功支付与逾期退款拒绝")
    fun `refund rejects non-success payments and requests after the refund window`() {
        mapOf(
            "PENDING" to 0,
            "PROCESSING" to 1,
            "FAILED" to 3,
            "CLOSED" to 4,
            "RESULT_UNKNOWN" to 5,
        ).forEach { (statusName, statusValue) ->
            val paymentId = postJson(
                "/api/payments",
                paymentRequest("REFUND-$statusName", "K-REFUND-$statusName", "40.00"),
                201,
            ).requiredText("paymentId")
            jdbcTemplate.update("update payment set status = ? where id = ?", statusValue, paymentId)
            val rejected = postJson(
                "/api/refunds",
                refundRequest(paymentId, "R-$statusName", "10.00", "2026-08-17T11:00:00Z"),
                400,
            )
            assertThat(rejected.requiredText("code")).isEqualTo("VALIDATION_ERROR")
            assertThat(getJson("/api/payments/$paymentId")["refundBudget"]["reservedAmount"].requiredText("amountMinor"))
                .isEqualTo("0")
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from refund where payment_id = ?",
                    Long::class.java,
                    paymentId,
                )
            ).isEqualTo(0L)
        }

        val succeeded = createSucceededPayment("REFUND-EXPIRED", "40.00")
        postJson(
            "/api/reference-fixtures/clock/set",
            mapOf("instant" to Instant.parse("2027-03-01T00:00:00Z")),
            200,
        )
        val expiredRefund = postJson(
            "/api/refunds",
            refundRequest(succeeded, "R-EXPIRED", "10.00", "2027-02-14T00:00:00Z"),
            400,
        )
        assertThat(expiredRefund.requiredText("code")).isEqualTo("VALIDATION_ERROR")
        assertThat(getJson("/api/payments/$succeeded")["refundBudget"]["reservedAmount"].requiredText("amountMinor"))
            .isEqualTo("0")
    }

    @Test
    @DisplayName("PAY-AC-023 — 退款 HTTP 并发预算防超退")
    fun `two concurrent refund HTTP applications persist one refund and return stable conflict`() {
        val paymentId = createSucceededPayment("REFUND-HTTP-CONCURRENT", "60.00")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = try {
            (1..2).map { index ->
                executor.submit<HttpJsonResult> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "concurrent HTTP refund requests were not released" }
                    postJsonResult(
                        "/api/refunds",
                        refundRequest(
                            paymentId,
                            "R-HTTP-CONCURRENT-$index",
                            "40.00",
                            "2026-08-17T11:00:0${index}Z",
                        ),
                    )
                }
            }.also {
                check(ready.await(5, TimeUnit.SECONDS)) { "concurrent HTTP refund requests did not rendezvous" }
                start.countDown()
            }
        } finally {
            // The executor remains alive until the submitted requests are collected below.
        }

        val results = try {
            futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertThat(results.map { it.status }.sorted()).containsExactly(201, 409)
        val created = results.single { it.status == 201 }.body
        val conflict = results.single { it.status == 409 }.body
        assertThat(created.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(created["idempotentReplay"].asBoolean()).isFalse()
        assertThat(conflict.requiredText("code")).isEqualTo("REFUND_BUDGET_EXCEEDED")
        assertThat(conflict.requiredText("message")).isNotBlank()

        val persistedRefund = getJson("/api/refunds/${created.requiredText("refundId")}")
        assertThat(persistedRefund.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(persistedRefund["reservationActive"].asBoolean()).isTrue()
        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("4000")
        assertThat(payment["refundBudget"]["succeededAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("2000")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refund where merchant_refund_number in (?, ?)",
                Long::class.java,
                "R-HTTP-CONCURRENT-1",
                "R-HTTP-CONCURRENT-2",
            )
        ).isEqualTo(1L)
    }

    @Test
    @DisplayName("PAY-AC-024 — 跨聚合事务回滚")
    fun `refund creation database failure rolls back payment reservation and refund aggregate together`() {
        val paymentId = createSucceededPayment("REFUND-UOW-ROLLBACK", "50.00")
        val overlongRefundNumber = "R-UOW-" + "X".repeat(3_000)

        val requestResult = runCatching {
            postJsonResult(
                "/api/refunds",
                refundRequest(paymentId, overlongRefundNumber, "10.00", "2026-08-17T11:00:00Z"),
            )
        }
        assertThat(
            requestResult.isFailure || requireNotNull(requestResult.getOrNull()).status == 409
        ).isTrue()
        requestResult.getOrNull()?.let { response ->
            assertThat(response.body.requiredText("code")).isEqualTo("CONCURRENT_MODIFICATION")
        }
        if (requestResult.isFailure) {
            assertThat(
                requestResult.exceptionOrNull()!!.causalChain()
                    .mapNotNull { it.message }
                    .joinToString(" | ")
                    .lowercase()
            ).containsAnyOf("value too long", "22001", "data exception")
        }

        val payment = getJson("/api/payments/$paymentId")
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["succeededAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("5000")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refund where payment_id = ?",
                Long::class.java,
                paymentId,
            )
        ).isEqualTo(0L)
    }
    @Test
    @DisplayName("PAY-AC-023 — 双事务预占防超退")
    fun `two real transactions cannot over-reserve one payment refund budget`() {
        val paymentId = createSucceededPayment("REFUND-CONCURRENT", "60.00")
        val ready = CountDownLatch(2)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                executor.submit {
                    try {
                        TransactionTemplate(transactionManager).executeWithoutResult {
                            val payment = requireNotNull(
                                entityManager.find(Payment::class.java, PaymentId.parse(paymentId))
                            )
                            ready.countDown()
                            check(ready.await(5, TimeUnit.SECONDS)) { "refund workers did not rendezvous" }
                            payment.reserveRefund(BigDecimal("40.00"))
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
        assertThat(payment["refundBudget"]["reservedAmount"].requiredText("amountMinor")).isEqualTo("4000")
        assertThat(payment["refundBudget"]["succeededAmount"].requiredText("amountMinor")).isEqualTo("0")
        assertThat(payment["refundBudget"]["availableAmount"].requiredText("amountMinor")).isEqualTo("2000")
    }

    @Test
    @DisplayName("PAY-AC-025 — 网关异常保持结果未知并继续占用预算")
    fun `refund gateway exception keeps attempt unknown and payment budget reserved`() {
        val paymentId = createSucceededPayment("REFUND-GATEWAY", "50.00")
        jdbcTemplate.update("update merchant_channel_configuration set channel_id = ? where merchant_id = ? and channel_id = ?", "C-THROW", "M-001", "C-001")
        try {
            val created = postJson("/api/refunds", mapOf("merchantId" to "M-001", "merchantRefundNumber" to "R-GATEWAY", "paymentId" to paymentId, "amount" to BigDecimal("20.00"), "currency" to "CNY", "requestedAt" to Instant.parse("2026-08-17T12:00:00Z")), 201)
            assertThat(created.requiredText("status")).isEqualTo("RESULT_UNKNOWN")
            assertThat(created.requiredText("diagnosticSummary"))
                .contains("退款渠道调用结果未知，保留原请求身份等待收敛")
                .doesNotContain("模拟的确定性渠道故障")
                .doesNotContain("RuntimeException")
            val refund = getJson("/api/refunds/${created.requiredText("refundId")}")
            assertThat(refund["reservationActive"].asBoolean()).isTrue()
            assertThat(refund["reservationReleased"].asBoolean()).isFalse()
            assertThat(refund["attempts"][0].requiredText("status")).isEqualTo("RESULT_UNKNOWN")
            assertThat(refund["attempts"][0].requiredText("rejectionSummary"))
                .contains("退款渠道调用结果未知，保留原请求身份等待收敛")
                .doesNotContain("模拟的确定性渠道故障")
        } finally { jdbcTemplate.update("update merchant_channel_configuration set channel_id = ? where merchant_id = ? and channel_id = ?", "C-001", "M-001", "C-THROW") }
    }

    private fun createSucceededPayment(prefix: String, amount: String): String {
        val created = postJson("/api/payments", paymentRequest(prefix, "K-$prefix", amount), 201); val paymentId = created.requiredText("paymentId")
        val attemptId = postJson("/api/payments/$paymentId/attempts", emptyMap<String, Any>(), 200).requiredText("paymentAttemptId")
        postJson("/api/channel/payment-results", mapOf("channelId" to "C-001", "notificationId" to "N-$prefix", "paymentId" to paymentId, "paymentAttemptId" to attemptId, "channelTransactionId" to "CT-$prefix", "amount" to BigDecimal(amount), "currency" to "CNY", "result" to "SUCCESS", "occurredAt" to Instant.parse("2026-08-17T10:30:00Z"), "verificationMaterial" to "test-secret"), 200); return paymentId
    }

    private fun createRefund(
        paymentId: String,
        merchantRefundNumber: String,
        amount: String,
        requestedAt: String,
    ): JsonNode = postJson(
        "/api/refunds",
        refundRequest(paymentId, merchantRefundNumber, amount, requestedAt),
        201,
    )

    private fun confirmRefund(
        refund: JsonNode,
        amount: String,
        notificationId: String,
        result: String,
        occurredAt: String,
    ): JsonNode = postJson(
        "/api/channel/refund-results",
        mapOf(
            "channelId" to "C-001",
            "notificationId" to notificationId,
            "refundId" to refund.requiredText("refundId"),
            "refundAttemptId" to refund.requiredText("refundAttemptId"),
            "channelRefundId" to "fake-refund-${refund.requiredText("requestIdentity")}",
            "amount" to BigDecimal(amount),
            "currency" to "CNY",
            "result" to result,
            "occurredAt" to Instant.parse(occurredAt),
            "verificationMaterial" to "test-secret",
        ),
        200,
    )

    private fun refundRequest(
        paymentId: String,
        merchantRefundNumber: String,
        amount: String,
        requestedAt: String,
    ): Map<String, Any> = mapOf(
        "merchantId" to "M-001",
        "merchantRefundNumber" to merchantRefundNumber,
        "paymentId" to paymentId,
        "amount" to BigDecimal(amount),
        "currency" to "CNY",
        "requestedAt" to Instant.parse(requestedAt),
    )

    private fun paymentCallback(
        paymentId: String,
        paymentAttemptId: String,
        notificationId: String,
        channelTransactionId: String,
        amount: String,
        result: String,
    ): Map<String, Any> = mapOf(
        "channelId" to "C-001",
        "notificationId" to notificationId,
        "paymentId" to paymentId,
        "paymentAttemptId" to paymentAttemptId,
        "channelTransactionId" to channelTransactionId,
        "amount" to BigDecimal(amount),
        "currency" to "CNY",
        "result" to result,
        "occurredAt" to Instant.parse("2026-08-22T00:30:00Z"),
        "verificationMaterial" to "test-secret",
    )

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
    )

    private data class HttpJsonResult(
        val status: Int,
        val body: JsonNode,
    )

    private fun postJsonResult(path: String, payload: Any): HttpJsonResult =
        if (isLegacyCombinedRefundRequest(path, payload)) submitLegacyRefund(payload) else performPost(path, payload)

    private fun performPost(path: String, payload: Any): HttpJsonResult {
        val requestPayload = prepareLegacyCallback(path, normalizeLegacyRequest(path, payload))
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(requestPayload))
        ).andReturn()
        val body = result.response.contentAsByteArray
            .takeIf { it.isNotEmpty() }
            ?.let(objectMapper::readTree)
            ?: objectMapper.createObjectNode()
        return HttpJsonResult(
            status = result.response.status,
            body = body,
        )
    }

    private fun postJson(path: String, payload: Any, expectedStatus: Int): JsonNode {
        if (isLegacyCombinedPaymentAttempt(path, payload) && expectedStatus == 200) {
            val createKey = "legacy-payment-attempt-${UUID.randomUUID()}"
            val created = postJson(path, mapOf("idempotencyKey" to createKey), expectedStatus = 201)
            return postJson(
                "$path/${created.requiredText("paymentAttemptId")}/submissions",
                mapOf("idempotencyKey" to "$createKey-submit"),
                expectedStatus = 200,
            )
        }
        val result = if (isLegacyCombinedRefundRequest(path, payload)) {
            submitLegacyRefund(payload)
        } else {
            performPost(path, payload)
        }
        assertThat(result.status).withFailMessage(result.body.toString()).isEqualTo(expectedStatus)
        return result.body
    }

    private fun postJsonAsActor(path: String, payload: Any, expectedStatus: Int): JsonNode {
        val requestPayload = prepareLegacyCallback(path, normalizeLegacyRequest(path, payload))
        val result = mockMvc.perform(
            post(path)
                .header("X-Reference-Actor-Context", "fixture-payment-reviewer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(requestPayload))
        ).andReturn()
        val body = result.response.contentAsByteArray
            .takeIf { it.isNotEmpty() }
            ?.let(objectMapper::readTree)
            ?: objectMapper.createObjectNode()
        assertThat(result.response.status).withFailMessage(body.toString()).isEqualTo(expectedStatus)
        return body
    }

    /** Drives older scenario fixtures through the unified Money and split-attempt HTTP contracts. */
    private fun normalizeLegacyRequest(path: String, payload: Any): Any {
        if (isLegacyCombinedPaymentAttempt(path, payload)) {
            return (payload as Map<*, *>).entries.associate { (key, value) -> key.toString() to value } +
                ("idempotencyKey" to "legacy-payment-attempt-${UUID.randomUUID()}")
        }
        if (payload !is Map<*, *>) return payload
        if (path == "/api/payments" && !payload.containsKey("money") &&
            payload.containsKey("amount") && payload.containsKey("currency")
        ) {
            return payload.entries
                .filterNot { (key, _) -> key == "amount" || key == "currency" }
                .associate { (key, value) -> key.toString() to value } + mapOf(
                "money" to money(payload),
            )
        }
        if (path == "/api/refunds" && !payload.containsKey("money") &&
            payload.containsKey("amount") && payload.containsKey("currency")
        ) {
            val merchantRefundNo = payload["merchantRefundNo"] ?: payload["merchantRefundNumber"]
            return payload.entries
                .filterNot { (key, _) -> key in setOf("amount", "currency", "merchantRefundNumber", "requestedAt") }
                .associate { (key, value) -> key.toString() to value } + mapOf(
                "idempotencyKey" to (payload["idempotencyKey"] ?: "legacy-refund-$merchantRefundNo"),
                "merchantRefundNo" to merchantRefundNo,
                "money" to money(payload),
                "reason" to (payload["reason"] ?: "reference refund scenario"),
            )
        }
        return payload
    }

    private fun money(payload: Map<*, *>): Map<String, Any?> = mapOf(
        "currency" to payload["currency"],
        "amountMinor" to (payload["amount"] as BigDecimal).movePointRight(2).stripTrailingZeros().toPlainString(),
    )

    private fun submitLegacyRefund(payload: Any): HttpJsonResult {
        val request = performPost("/api/refunds", payload)
        if (request.status != 201) return request
        val refundId = request.body.requiredText("refundId")
        val normalized = normalizeLegacyRequest("/api/refunds", payload) as Map<*, *>
        val refundNo = normalized["merchantRefundNo"].toString()
        val attempt = performPost(
            "/api/refunds/$refundId/attempts",
            mapOf("idempotencyKey" to "legacy-refund-attempt-$refundNo"),
        )
        if (attempt.status != 201) return attempt
        val attemptId = attempt.body.requiredText("refundAttemptId")
        val submitted = performPost(
            "/api/refunds/$refundId/attempts/$attemptId/submissions",
            mapOf("idempotencyKey" to "legacy-refund-submit-$refundNo"),
        )
        if (submitted.status !in 200..299) return submitted
        val composite = objectMapper.createObjectNode()
        sequenceOf(request.body, attempt.body, submitted.body).forEach { source ->
            source.fields().forEachRemaining { (name, value) -> composite.set<JsonNode>(name, value) }
        }
        composite.put("status", submitted.body.path("refundStatus").asText())
        composite.put("idempotentReplay", request.body.path("idempotentReplay").asBoolean())
        return HttpJsonResult(status = 201, body = composite)
    }

    private fun prepareLegacyCallback(path: String, payload: Any): Any {
        if (path !in setOf("/api/channel/payment-results", "/api/channel/refund-results") || payload !is Map<*, *>) {
            return payload
        }
        val verificationMaterial = payload["verificationMaterial"]?.toString() ?: return payload
        val kind = if (path.endsWith("payment-results")) "PAYMENT" else "REFUND"
        val externalIdentity = payload["notificationId"].toString()
        val associationIdentity = if (kind == "PAYMENT") {
            listOf(payload["paymentId"], payload["paymentAttemptId"], payload["channelTransactionId"])
        } else {
            listOf(payload["refundId"], payload["refundAttemptId"], payload["channelRefundId"])
        }.joinToString("|")
        val rawPayload = "legacy-${kind.lowercase()}-$externalIdentity"
        if (verificationMaterial == "test-secret") {
            val evidence = performRawPost(
                "/api/reference-fixtures/callback-evidence",
                mapOf(
                    "idempotencyKey" to "legacy-evidence-$kind-$externalIdentity",
                    "kind" to kind,
                    "channelId" to payload["channelId"],
                    "externalIdentity" to externalIdentity,
                    "associationIdentity" to associationIdentity,
                    "money" to money(payload),
                    "rawPayload" to rawPayload,
                ),
            )
            check(evidence.status in 200..299 || evidence.status == 409) {
                "reference callback evidence registration failed: ${evidence.status} ${evidence.body}"
            }
        }
        return payload.entries
            .filterNot { (key, _) -> key in setOf("verificationMaterial", "amount", "currency") }
            .associate { (key, value) -> key.toString() to value } +
            mapOf(
                "money" to money(payload),
                "rawPayload" to if (verificationMaterial == "test-secret") rawPayload else "untrusted-$rawPayload",
            )
    }

    private fun performRawPost(path: String, payload: Any): HttpJsonResult {
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload))
        ).andReturn()
        val body = result.response.contentAsByteArray
            .takeIf { it.isNotEmpty() }
            ?.let(objectMapper::readTree)
            ?: objectMapper.createObjectNode()
        return HttpJsonResult(result.response.status, body)
    }

    private fun isLegacyCombinedPaymentAttempt(path: String, payload: Any): Boolean =
        path.matches(Regex("/api/payments/[^/]+/attempts")) &&
            payload is Map<*, *> && !payload.containsKey("idempotencyKey")

    private fun isLegacyCombinedRefundRequest(path: String, payload: Any): Boolean =
        path == "/api/refunds" && payload is Map<*, *> && !payload.containsKey("money")

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
