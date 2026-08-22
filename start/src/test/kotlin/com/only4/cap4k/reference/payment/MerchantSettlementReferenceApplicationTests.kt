package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.transfer.StartSettlementTransferHandler
import com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.channel.ChannelStatementFixtureStore
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.adjustment.ReturnMerchantSettlementForAdjustmentCmd
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.lifecycle.ActivateMerchantSettlementCmd
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.transfer.StartSettlementTransfer
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review.AdjudicateMerchantSettlementResultCmd
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review.ReviewUnknownMerchantSettlementsCmd
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RunDailyReconciliationCmd
import com.only4.cap4k.reference.payment.application.subscribers.domain.merchant_settlement.MerchantSettlementCompletedDomainEventSubscriber
import com.only4.cap4k.reference.payment.contract.events.integration.outbound.merchant_settlement.MerchantSettlementCompletedIntegrationEvent
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatementRecord
import com.sun.net.httpserver.HttpServer
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.mockito.Mockito

@SpringBootTest
@AutoConfigureMockMvc
class MerchantSettlementReferenceApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val objectMapper: ObjectMapper,
    @param:Autowired private val statements: ChannelStatementFixtureStore,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
    @param:Autowired private val reliableEventCoordinator: ReliableEventCoordinator,
) {

    @field:MockitoSpyBean
    private lateinit var transferHandler: StartSettlementTransferHandler

    @field:MockitoSpyBean
    private lateinit var activationHandler: ActivateMerchantSettlementCmd.Handler

    @field:MockitoSpyBean
    private lateinit var completedSubscriber: MerchantSettlementCompletedDomainEventSubscriber

    @Test
    fun `merchant settlement lifecycle produces net 127 and preserves callback evidence`() {
        val date = LocalDate.parse("2026-06-11")
        val payment100 = createSucceededPayment("B4-LIFECYCLE-100", "100.00", "2026-06-11T02:00:00Z")
        val payment50 = createSucceededPayment("B4-LIFECYCLE-50", "50.00", "2026-06-11T03:00:00Z")
        val refund20 = createSucceededRefund(
            paymentId = payment100.paymentId,
            merchantRefundNumber = "R-B4-LIFECYCLE-20",
            amount = "20.00",
            requestedAt = "2026-06-11T04:00:00Z",
            occurredAt = "2026-06-11T05:00:00Z",
        )
        reconcile(
            date = date,
            identity = "statement-b4-lifecycle",
            payments = listOf(payment100 to "100.00", payment50 to "50.00"),
            refunds = listOf(refund20 to "20.00"),
        )

        val prepared = prepare(date, "b4-lifecycle")
        assertThat(prepared.requiredText("status")).isEqualTo("PREPARED")
        assertThat(prepared["created"].asBoolean()).isTrue()
        assertThat(prepared["eligibleCount"].asInt()).isEqualTo(3)
        assertThat(prepared["paymentGrossAmount"].decimalValue()).isEqualByComparingTo("150.00")
        assertThat(prepared["refundGrossAmount"].decimalValue()).isEqualByComparingTo("20.00")
        assertThat(prepared["feeTotalAmount"].decimalValue()).isEqualByComparingTo("3.00")
        assertThat(prepared["netAmount"].decimalValue()).isEqualByComparingTo("127.00")
        val settlementId = prepared.requiredText("settlementId")

        val replay = prepare(date, "b4-lifecycle-replay")
        assertThat(replay.requiredText("settlementId")).isEqualTo(settlementId)
        assertThat(replay["idempotentReplay"].asBoolean()).isTrue()

        var settlement = getJson("/api/merchant-settlements/$settlementId")
        assertThat(settlement["lines"]).hasSize(3)
        assertThat(settlement["lines"].elements().asSequence().map { it["feeAmount"].decimalValue().stripTrailingZeros() }.toList())
            .containsExactlyInAnyOrder(BigDecimal("2"), BigDecimal("1"), BigDecimal.ZERO)

        val confirmed = postJson(
            "/api/merchant-settlements/$settlementId/confirmations",
            mapOf(
                "settlementId" to settlementId,
                "operatorIdentity" to "settlement-operator-1",
                "operatorRole" to "SETTLEMENT_OPERATOR",
                "confirmedAt" to Instant.parse("2026-06-12T02:00:00Z"),
            ),
            expectedStatus = 200,
        )
        assertThat(confirmed.requiredText("status")).isEqualTo("CONFIRMED")
        assertThat(confirmed["netAmount"].decimalValue()).isEqualByComparingTo("127.00")

        val execution = postJson(
            "/api/merchant-settlements/$settlementId/executions",
            mapOf(
                "settlementId" to settlementId,
                "operatorIdentity" to "settlement-operator-1",
                "operatorRole" to "SETTLEMENT_OPERATOR",
                "requestedAt" to Instant.parse("2026-06-12T03:00:00Z"),
            ),
            expectedStatus = 200,
        )
        assertThat(execution.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(execution["providerAccepted"].asBoolean()).isTrue()
        val attemptId = execution.requiredText("attemptId")
        val groupIdentity = execution.requiredText("executionGroupIdentity")
        val requestIdentity = execution.requiredText("requestIdentity")
        val externalIdentity = "STL-$requestIdentity"

        val successPayload = settlementResultRequest(
            settlementId = settlementId,
            attemptId = attemptId,
            groupIdentity = groupIdentity,
            requestIdentity = requestIdentity,
            externalIdentity = externalIdentity,
            notificationId = "N-B4-LIFECYCLE-SUCCESS",
            amount = "127.00",
            result = "SUCCESS",
            occurredAt = "2026-06-12T03:05:00Z",
            receivedAt = "2026-06-12T03:05:30Z",
        )
        val success = postJson("/api/channel/settlement-results", successPayload, expectedStatus = 200)
        assertThat(success.requiredText("settlementStatus")).isEqualTo("SUCCEEDED")
        assertThat(success.requiredText("disposition")).isEqualTo("SUCCESS_ACCEPTED")
        assertThat(success["settledFactFormedNow"].asBoolean()).isTrue()

        val replayPayload = successPayload.toMutableMap().apply {
            this["receivedAt"] = Instant.parse("2026-06-12T03:06:00Z")
        }
        val duplicate = postJson("/api/channel/settlement-results", replayPayload, expectedStatus = 200)
        assertThat(duplicate.requiredText("disposition")).isEqualTo("ACCEPTED_DUPLICATE")
        assertThat(duplicate["notificationReceiveCount"].asInt()).isEqualTo(2)
        assertThat(duplicate["settledFactFormedNow"].asBoolean()).isFalse()

        val lateFailure = postJson(
            "/api/channel/settlement-results",
            settlementResultRequest(
                settlementId = settlementId,
                attemptId = attemptId,
                groupIdentity = groupIdentity,
                requestIdentity = requestIdentity,
                externalIdentity = externalIdentity,
                notificationId = "N-B4-LIFECYCLE-LATE-FAILURE",
                amount = "127.00",
                result = "FAILED",
                occurredAt = "2026-06-12T03:07:00Z",
                receivedAt = "2026-06-12T03:07:30Z",
            ),
            expectedStatus = 200,
        )
        assertThat(lateFailure.requiredText("settlementStatus")).isEqualTo("SUCCEEDED")
        assertThat(lateFailure.requiredText("disposition")).isEqualTo("CONFLICT")

        settlement = getJson("/api/merchant-settlements/$settlementId")
        assertThat(settlement.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(settlement["settledFactFormed"].asBoolean()).isTrue()
        assertThat(settlement["attempts"]).hasSize(1)
        val attempt = settlement["attempts"][0]
        assertThat(attempt.requiredText("status")).isEqualTo("CONFLICT_REVIEW_REQUIRED")
        assertThat(attempt["notificationReceiveCount"].asInt()).isEqualTo(3)
        assertThat(attempt["receipts"]).hasSize(2)
        assertThat(completedEventCount(settlementId)).isEqualTo(1L)
        assertThat(attempt["receipts"].arrayItem("notificationIdentity", "N-B4-LIFECYCLE-SUCCESS")["receiveCount"].asInt()).isEqualTo(2)
        assertThat(attempt["receipts"].arrayItem("notificationIdentity", "N-B4-LIFECYCLE-LATE-FAILURE").requiredText("decision"))
            .isEqualTo("CONFLICT")
    }

    @Test
    fun `unknown result blocks retry until review and manual adjudication`() {
        val date = LocalDate.parse("2026-06-12")
        val payment = createSucceededPayment("B4-UNKNOWN", "100.00", "2026-06-12T02:00:00Z")
        reconcile(date, "statement-b4-unknown", payments = listOf(payment to "100.00"))
        val settlementId = prepare(date, "b4-unknown").requiredText("settlementId")
        confirm(settlementId, "2026-06-13T02:00:00Z")
        val execution = startExecution(settlementId, "2026-06-13T03:00:00Z")
        val attemptId = execution.requiredText("attemptId")
        val groupIdentity = execution.requiredText("executionGroupIdentity")
        val requestIdentity = execution.requiredText("requestIdentity")

        val unknown = postJson(
            "/api/channel/settlement-results",
            settlementResultRequest(
                settlementId = settlementId,
                attemptId = attemptId,
                groupIdentity = groupIdentity,
                requestIdentity = requestIdentity,
                externalIdentity = "STL-$requestIdentity",
                notificationId = "N-B4-UNKNOWN",
                amount = "98.00",
                result = "UNKNOWN",
                occurredAt = "2026-06-13T03:05:00Z",
                receivedAt = "2026-06-13T03:05:30Z",
            ),
            expectedStatus = 200,
        )
        assertThat(unknown.requiredText("settlementStatus")).isEqualTo("RESULT_UNKNOWN")
        assertThat(unknown.requiredText("disposition")).isEqualTo("UNKNOWN_ACCEPTED")

        val retry = postJson(
            "/api/merchant-settlements/$settlementId/executions",
            mapOf(
                "settlementId" to settlementId,
                "operatorIdentity" to "settlement-operator-1",
                "operatorRole" to "SETTLEMENT_OPERATOR",
                "requestedAt" to Instant.parse("2026-06-13T03:10:00Z"),
            ),
            expectedStatus = 409,
        )
        assertThat(retry.requiredText("code")).isNotBlank()

        val review = Mediator.commands.send(
            ReviewUnknownMerchantSettlementsCmd.Request(Instant.parse("2026-06-13T03:40:00Z"))
        )
        assertThat(review.reviewedCount).isEqualTo(1)

        val adjudicated = Mediator.commands.send(
            AdjudicateMerchantSettlementResultCmd.Request(
                settlementId = settlementId,
                executionAttemptId = attemptId,
                operatorIdentity = "settlement-operator-2",
                operatorRole = "SETTLEMENT_OPERATOR",
                finalResult = "SUCCESS",
                adjudicatedAt = Instant.parse("2026-06-13T03:45:00Z"),
                evidence = "operator confirmed the transfer in the channel console",
            )
        )
        assertThat(adjudicated.outcome.settlementStatus.name).isEqualTo("SUCCEEDED")
        assertThat(adjudicated.outcome.settledFactFormedNow).isTrue()

        val settlement = getJson("/api/merchant-settlements/$settlementId")
        assertThat(settlement.requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(settlement["attempts"]).hasSize(1)
        assertThat(settlement["attempts"][0]["receipts"]).hasSize(2)
        assertThat(settlement["attempts"][0]["finalResult"].asText()).isEqualTo("SUCCESS")
        assertThat(completedEventCount(settlementId)).isEqualTo(1L)
    }

    @Test
    fun `settlement success and outbound event record roll back together when the completion subscriber fails`() {
        val date = LocalDate.parse("2026-06-28")
        val payment = createSucceededPayment("B5-OUTBOX-ROLLBACK", "100.00", "2026-06-28T02:00:00Z")
        reconcile(date, "statement-b5-outbox-rollback", payments = listOf(payment to "100.00"))
        val settlementId = prepare(date, "b5-outbox-rollback").requiredText("settlementId")
        confirm(settlementId, "2026-06-29T02:00:00Z")
        val execution = startExecution(settlementId, "2026-06-29T03:00:00Z")

        Mockito.doAnswer { invocation ->
            invocation.callRealMethod()
            throw IllegalStateException("force rollback after outbound event enqueue")
        }.`when`(completedSubscriber).on(mockitoAny())
        val result = try {
            postJsonResult(
                "/api/channel/settlement-results",
                settlementResultRequest(
                    settlementId = settlementId,
                    attemptId = execution.requiredText("attemptId"),
                    groupIdentity = execution.requiredText("executionGroupIdentity"),
                    requestIdentity = execution.requiredText("requestIdentity"),
                    externalIdentity = "STL-${execution.requiredText("requestIdentity")}",
                    notificationId = "N-B5-OUTBOX-ROLLBACK",
                    amount = "98.00",
                    result = "SUCCESS",
                    occurredAt = "2026-06-29T03:05:00Z",
                    receivedAt = "2026-06-29T03:05:30Z",
                ),
            )
        } finally {
            Mockito.reset(completedSubscriber)
        }
        assertThat(result.status).isNotIn(200, 201)

        val settlement = getJson("/api/merchant-settlements/$settlementId")
        assertThat(settlement.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(settlement["settledFactFormed"].asBoolean()).isFalse()
        assertThat(settlement["attempts"][0].requiredText("status")).isEqualTo("PROCESSING")
        assertThat(settlement["attempts"][0]["finalResult"].isNull).isTrue()
        assertThat(completedEventCount(settlementId)).isZero()
    }

    @Test
    fun `outbound HTTP event keeps one identity across failed handoff and durable retry`() {
        val receivedBodies = CopyOnWriteArrayList<String>()
        val responseStatus = AtomicInteger(503)
        val receiverPort = requireNotNull(System.getProperty("payment.reference.test.integration-event-port")) {
            "The Gradle test task must provide payment.reference.test.integration-event-port"
        }.toInt()
        val receiver = HttpServer.create(InetSocketAddress("127.0.0.1", receiverPort), 0)
        receiver.createContext("/cap4k/integration-events") { exchange ->
            try {
                receivedBodies += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                exchange.sendResponseHeaders(responseStatus.get(), -1)
            } finally {
                exchange.close()
            }
        }
        receiver.start()

        val eventIdentity = "merchant-settlement-completed-http-retry"
        val event = MerchantSettlementCompletedIntegrationEvent(
            eventIdentity = eventIdentity,
            settlementId = "settlement-http-retry",
            merchantId = "M-001",
            channelId = "C-001",
            currency = "CNY",
            netAmount = BigDecimal("127.00"),
            completedAt = Instant.parse("2026-08-22T04:00:00Z"),
        )

        try {
            Mediator.commands.send(EnqueueMerchantSettlementCompletedForTestCmd.Request(event))

            val failed = awaitReliableEvent(eventIdentity, expectedState = -9, minimumTriedTimes = 1)
            val firstEnvelope = awaitEventEnvelopes(receivedBodies, eventIdentity, minimumCount = 1).single()
            assertThat(firstEnvelope.eventId).isEqualTo(failed.eventUuid)
            assertThat(firstEnvelope.eventType).isEqualTo(MerchantSettlementCompletedIntegrationEvent.EVENT_NAME)
            assertThat(firstEnvelope.deliveryAttempt).isEqualTo(1)

            responseStatus.set(204)
            val delivered = awaitReliableEvent(
                eventIdentity = eventIdentity,
                expectedState = 1,
                minimumTriedTimes = 2,
                retrySignal = {
                    jdbcTemplate.update(
                        "update __event set next_try_time = current_timestamp where event_uuid = ?",
                        failed.eventUuid,
                    )
                    reliableEventCoordinator.wake()
                },
            )
            val envelopes = awaitEventEnvelopes(receivedBodies, eventIdentity, minimumCount = 2)
                .sortedBy { it.deliveryAttempt }
            assertThat(delivered.eventUuid).isEqualTo(failed.eventUuid)
            assertThat(delivered.triedTimes).isEqualTo(2)
            assertThat(envelopes.map { it.eventId }).containsOnly(failed.eventUuid)
            assertThat(envelopes.map { it.eventType }).containsOnly(MerchantSettlementCompletedIntegrationEvent.EVENT_NAME)
            assertThat(envelopes.map { it.deliveryAttempt }).containsExactly(1, 2)
            assertThat(envelopes.map { it.payloadJson }.distinct()).hasSize(1)
        } finally {
            receiver.stop(0)
        }
    }

    @Test
    fun `outbound HTTP event remains retryable after response timeout and recovers with the same identity`() {
        val receivedBodies = CopyOnWriteArrayList<String>()
        val delayFirstResponse = AtomicBoolean(true)
        val firstRequestStarted = CountDownLatch(1)
        val releaseFirstResponse = CountDownLatch(1)
        val firstRequestFinished = CountDownLatch(1)
        val receiverPort = requireNotNull(System.getProperty("payment.reference.test.integration-event-port")) {
            "The Gradle test task must provide payment.reference.test.integration-event-port"
        }.toInt()
        val receiver = HttpServer.create(InetSocketAddress("127.0.0.1", receiverPort), 0)
        receiver.createContext("/cap4k/integration-events") { exchange ->
            val delayedAttempt = delayFirstResponse.compareAndSet(true, false)
            try {
                receivedBodies += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                if (delayedAttempt) {
                    firstRequestStarted.countDown()
                    check(releaseFirstResponse.await(20, TimeUnit.SECONDS)) {
                        "The timeout test did not release the delayed HTTP response"
                    }
                }
                exchange.sendResponseHeaders(204, -1)
            } finally {
                if (delayedAttempt) firstRequestFinished.countDown()
                exchange.close()
            }
        }
        receiver.start()

        val eventIdentity = "merchant-settlement-completed-http-timeout-retry"
        val event = MerchantSettlementCompletedIntegrationEvent(
            eventIdentity = eventIdentity,
            settlementId = "settlement-http-timeout-retry",
            merchantId = "M-001",
            channelId = "C-001",
            currency = "CNY",
            netAmount = BigDecimal("127.00"),
            completedAt = Instant.parse("2026-08-22T05:00:00Z"),
        )

        try {
            Mediator.commands.send(EnqueueMerchantSettlementCompletedForTestCmd.Request(event))
            assertThat(firstRequestStarted.await(2, TimeUnit.SECONDS)).isTrue()

            val failed = awaitReliableEvent(eventIdentity, expectedState = -9, minimumTriedTimes = 1)
            assertThat(firstRequestFinished.count).isEqualTo(1L)
            val failureFacts = jdbcTemplate.queryForObject(
                "select failure_facts from __event where event_uuid = ?",
                String::class.java,
                failed.eventUuid,
            )
            assertThat(failureFacts)
                .contains("HttpIntegrationEventHandoffException")
                .contains("\"retryable\":true")

            releaseFirstResponse.countDown()
            assertThat(firstRequestFinished.await(2, TimeUnit.SECONDS)).isTrue()
            val delivered = awaitReliableEvent(
                eventIdentity = eventIdentity,
                expectedState = 1,
                minimumTriedTimes = 2,
                retrySignal = {
                    jdbcTemplate.update(
                        "update __event set next_try_time = current_timestamp where event_uuid = ?",
                        failed.eventUuid,
                    )
                    reliableEventCoordinator.wake()
                },
            )
            val envelopes = awaitEventEnvelopes(receivedBodies, eventIdentity, minimumCount = 2)
                .sortedBy { it.deliveryAttempt }
            assertThat(delivered.eventUuid).isEqualTo(failed.eventUuid)
            assertThat(delivered.triedTimes).isEqualTo(2)
            assertThat(envelopes.map { it.eventId }).containsOnly(failed.eventUuid)
            assertThat(envelopes.map { it.eventType }).containsOnly(MerchantSettlementCompletedIntegrationEvent.EVENT_NAME)
            assertThat(envelopes.map { it.deliveryAttempt }).containsExactly(1, 2)
            assertThat(envelopes.map { it.payloadJson }.distinct()).hasSize(1)
        } finally {
            releaseFirstResponse.countDown()
            receiver.stop(0)
        }
    }

    @Test
    fun `void replacement keeps a single effective settlement and preserves source evidence`() {
        val date = LocalDate.parse("2026-06-13")
        val payment = createSucceededPayment("B4-VOID", "100.00", "2026-06-13T02:00:00Z")
        reconcile(date, "statement-b4-void", payments = listOf(payment to "100.00"))
        val original = prepare(date, "b4-void")
        val originalId = original.requiredText("settlementId")
        val originalView = getJson("/api/merchant-settlements/$originalId")
        val sourceIdentity = originalView["lines"][0].requiredText("sourceFactIdentity")

        val voided = postJson(
            "/api/merchant-settlements/$originalId/voids",
            mapOf(
                "settlementId" to originalId,
                "operatorIdentity" to "settlement-operator-1",
                "operatorRole" to "SETTLEMENT_OPERATOR",
                "reason" to "replace the settlement after an operator review",
                "voidedAt" to Instant.parse("2026-06-14T02:00:00Z"),
                "createReplacement" to true,
            ),
            expectedStatus = 200,
        )
        assertThat(voided.requiredText("status")).isEqualTo("VOIDED")
        val replacementId = voided.requiredText("replacementSettlementId")
        assertThat(replacementId).isNotEqualTo(originalId)

        val replay = prepare(date, "b4-void-replay")
        assertThat(replay.requiredText("settlementId")).isEqualTo(replacementId)
        assertThat(replay["idempotentReplay"].asBoolean()).isTrue()

        val oldView = getJson("/api/merchant-settlements/$originalId")
        val replacement = getJson("/api/merchant-settlements/$replacementId")
        assertThat(oldView.requiredText("status")).isEqualTo("VOIDED")
        assertThat(oldView.requiredText("replacementSettlementId")).isEqualTo(replacementId)
        assertThat(replacement.requiredText("predecessorSettlementId")).isEqualTo(originalId)
        assertThat(replacement["lines"][0].requiredText("sourceFactIdentity")).isEqualTo(sourceIdentity)
        assertThat(replacement["netAmount"].decimalValue()).isEqualByComparingTo(originalView["netAmount"].decimalValue())
    }

    @Test
    fun `unconfirmed settlement can return for adjustment and confirmed settlement cannot`() {
        val date = LocalDate.parse("2026-06-14")
        val payment = createSucceededPayment("B4-ADJUSTMENT", "100.00", "2026-06-14T02:00:00Z")
        reconcile(date, "statement-b4-adjustment", payments = listOf(payment to "100.00"))
        val originalId = prepare(date, "b4-adjustment").requiredText("settlementId")

        val returned = Mediator.commands.send(
            ReturnMerchantSettlementForAdjustmentCmd.Request(
                settlementId = originalId,
                operatorIdentity = "settlement-operator-1",
                operatorRole = "SETTLEMENT_OPERATOR",
                reason = "rebuild after adjustment evidence was reviewed",
                returnedAt = Instant.parse("2026-06-15T02:00:00Z"),
            )
        )
        assertThat(returned.previousStatus).isEqualTo("VOIDED")
        assertThat(returned.replacementStatus).isEqualTo("PREPARED")
        val replacementId = returned.replacementSettlementId

        val replayedReturn = Mediator.commands.send(
            ReturnMerchantSettlementForAdjustmentCmd.Request(
                settlementId = originalId,
                operatorIdentity = "settlement-operator-1",
                operatorRole = "SETTLEMENT_OPERATOR",
                reason = "same request replay",
                returnedAt = Instant.parse("2026-06-15T02:01:00Z"),
            )
        )
        assertThat(replayedReturn.replacementSettlementId).isEqualTo(replacementId)
        assertThat(prepare(date, "b4-adjustment-replay").requiredText("settlementId")).isEqualTo(replacementId)

        confirm(replacementId, "2026-06-15T03:00:00Z")
        assertThatThrownBy {
            Mediator.commands.send(
                ReturnMerchantSettlementForAdjustmentCmd.Request(
                    settlementId = replacementId,
                    operatorIdentity = "settlement-operator-1",
                    operatorRole = "SETTLEMENT_OPERATOR",
                    reason = "should be rejected after confirmation",
                    returnedAt = Instant.parse("2026-06-15T03:05:00Z"),
                )
            )
        }.hasMessageContaining("cannot be returned for adjustment")
    }

    @Test
    fun `configuration changes do not rewrite frozen payment fees or settlement lines`() {
        val date = LocalDate.parse("2026-06-18")
        val payment = createSucceededPayment("B4-FEE-SNAPSHOT", "100.00", "2026-06-18T02:00:00Z")
        assertThat(
            jdbcTemplate.queryForObject(
                "select settlement_fee_basis_points from payment where id = ?",
                Int::class.java,
                payment.paymentId,
            )
        ).isEqualTo(200)

        jdbcTemplate.update(
            "update merchant_channel_configuration set settlement_fee_basis_points = 800 where merchant_id = 'M-001' and channel_id = 'C-001' and currency = 'CNY' and payment_method = 'CARD'"
        )
        try {
            reconcile(date, "statement-b4-fee-snapshot", payments = listOf(payment to "100.00"))
            val prepared = prepare(date, "b4-fee-snapshot")
            assertThat(prepared["feeTotalAmount"].decimalValue()).isEqualByComparingTo("2.00")
            assertThat(prepared["netAmount"].decimalValue()).isEqualByComparingTo("98.00")

            val settlement = getJson("/api/merchant-settlements/${prepared.requiredText("settlementId")}")
            val line = settlement["lines"].single()
            assertThat(line["feeBasisPoints"].asInt()).isEqualTo(200)
            assertThat(line.requiredText("feeRoundingMode")).isEqualTo("HALF_UP")
            assertThat(line["feeCalculationAmount"].decimalValue()).isEqualByComparingTo("100.00")
            assertThat(line["feeAmount"].decimalValue()).isEqualByComparingTo("2.00")
            assertThat(
                jdbcTemplate.queryForObject(
                    "select settlement_fee_amount from payment where id = ?",
                    BigDecimal::class.java,
                    payment.paymentId,
                )
            ).isEqualByComparingTo("2.00")
        } finally {
            jdbcTemplate.update(
                "update merchant_channel_configuration set settlement_fee_basis_points = 200 where merchant_id = 'M-001' and channel_id = 'C-001' and currency = 'CNY' and payment_method = 'CARD'"
            )
        }
    }

    @Test
    fun `one unresolved reconciliation item is excluded while another matched payment settles`() {
        val date = LocalDate.parse("2026-06-19")
        val matched = createSucceededPayment("B4-PARTIAL-MATCHED", "100.00", "2026-06-19T02:00:00Z")
        val blocked = createSucceededPayment("B4-PARTIAL-BLOCKED", "50.00", "2026-06-19T03:00:00Z")
        val identity = "statement-b4-partial-exclusion"
        statements.publish(
            statement(
                identity = identity,
                revision = "1",
                date = date,
                records = listOf(
                    record("$identity-matched", ReconciliationTransactionKind.PAYMENT, matched.channelTransactionId, "100.00", matched.occurredAt),
                    record("$identity-blocked", ReconciliationTransactionKind.PAYMENT, blocked.channelTransactionId, "49.00", blocked.occurredAt),
                ),
            )
        )
        val reconciliation = Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = date.plusDays(1).atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
            )
        )
        assertThat(reconciliation.batchStatus).isEqualTo("AWAITING_DISPOSITION")
        assertThat(reconciliation.unresolvedDifferenceCount).isEqualTo(1)

        val prepared = prepare(date, "b4-partial-exclusion")
        assertThat(prepared["eligibleCount"].asInt()).isEqualTo(1)
        assertThat(prepared["excludedCount"].asInt()).isEqualTo(1)
        assertThat(prepared.requiredText("blockerSummary")).contains("AMOUNT_MISMATCH").contains("settlement-blocking")
        assertThat(prepared["paymentGrossAmount"].decimalValue()).isEqualByComparingTo("100.00")
        assertThat(prepared["feeTotalAmount"].decimalValue()).isEqualByComparingTo("2.00")
        assertThat(prepared["netAmount"].decimalValue()).isEqualByComparingTo("98.00")

        val settlement = getJson("/api/merchant-settlements/${prepared.requiredText("settlementId")}")
        assertThat(settlement["lines"]).hasSize(1)
        assertThat(settlement["lines"][0].requiredText("paymentId")).isEqualTo(matched.paymentId)
        assertThat(settlement["excludedCount"].asInt()).isEqualTo(1)
        assertThat(settlement.requiredText("blockerSummary")).contains("AMOUNT_MISMATCH")
    }

    @Test
    fun `negative and zero net settlements never invoke the transfer provider`() {
        val negativeDate = LocalDate.parse("2026-06-20")
        val negativePayment = createSucceededPayment("B4-NEGATIVE", "100.00", "2026-06-20T02:00:00Z")
        val fullRefund = createSucceededRefund(
            paymentId = negativePayment.paymentId,
            merchantRefundNumber = "R-B4-NEGATIVE-100",
            amount = "100.00",
            requestedAt = "2026-06-20T03:00:00Z",
            occurredAt = "2026-06-20T04:00:00Z",
        )
        reconcile(
            negativeDate,
            "statement-b4-negative",
            payments = listOf(negativePayment to "100.00"),
            refunds = listOf(fullRefund to "100.00"),
        )
        Mockito.clearInvocations(transferHandler)
        val negative = prepare(negativeDate, "b4-negative")
        val negativeId = negative.requiredText("settlementId")
        assertThat(negative.requiredText("status")).isEqualTo("NEGATIVE_REVIEW_REQUIRED")
        assertThat(negative["netAmount"].decimalValue()).isEqualByComparingTo("-2.00")
        assertThat(confirm(negativeId, "2026-06-21T02:00:00Z").requiredText("status"))
            .isEqualTo("NEGATIVE_REVIEW_REQUIRED")
        val negativeExecution = postJsonResult(
            "/api/merchant-settlements/$negativeId/executions",
            mapOf(
                "settlementId" to negativeId,
                "operatorIdentity" to "settlement-operator-1",
                "operatorRole" to "SETTLEMENT_OPERATOR",
                "requestedAt" to Instant.parse("2026-06-21T03:00:00Z"),
            ),
        )
        assertThat(negativeExecution.status).isEqualTo(400)
        assertThat(negativeExecution.body.requiredText("code")).isEqualTo("INVALID_REQUEST")
        Mockito.verifyNoInteractions(transferHandler)
        val negativeView = getJson("/api/merchant-settlements/$negativeId")
        assertThat(negativeView["lines"]).hasSize(2)
        assertThat(negativeView["attempts"]).isEmpty()

        val zeroDate = LocalDate.parse("2026-06-21")
        val zeroPayment = createSucceededPayment("B4-ZERO", "100.00", "2026-06-21T02:00:00Z")
        val refund98 = createSucceededRefund(
            paymentId = zeroPayment.paymentId,
            merchantRefundNumber = "R-B4-ZERO-98",
            amount = "98.00",
            requestedAt = "2026-06-21T03:00:00Z",
            occurredAt = "2026-06-21T04:00:00Z",
        )
        reconcile(
            zeroDate,
            "statement-b4-zero",
            payments = listOf(zeroPayment to "100.00"),
            refunds = listOf(refund98 to "98.00"),
        )
        Mockito.clearInvocations(transferHandler)
        val zero = prepare(zeroDate, "b4-zero")
        val zeroId = zero.requiredText("settlementId")
        assertThat(zero["netAmount"].decimalValue()).isEqualByComparingTo("0.00")
        assertThat(confirm(zeroId, "2026-06-22T02:00:00Z").requiredText("status")).isEqualTo("SUCCEEDED")
        assertThat(getJson("/api/merchant-settlements/$zeroId")["attempts"]).isEmpty()
        Mockito.verifyNoInteractions(transferHandler)
    }

    @Test
    fun `replacement activation failure rolls back predecessor release and replacement creation`() {
        val date = LocalDate.parse("2026-06-22")
        val payment = createSucceededPayment("B4-ACTIVATION-ROLLBACK", "100.00", "2026-06-22T02:00:00Z")
        reconcile(date, "statement-b4-activation-rollback", payments = listOf(payment to "100.00"))
        val originalId = prepare(date, "b4-activation-rollback").requiredText("settlementId")
        val scopeIdentity = "DAILY|M-001|C-001|CNY|$date"

        Mockito.doThrow(IllegalStateException("forced replacement activation failure"))
            .`when`(activationHandler).handle(mockitoAny())
        val result = runCatching {
            postJsonResult(
                "/api/merchant-settlements/$originalId/voids",
                mapOf(
                    "settlementId" to originalId,
                    "operatorIdentity" to "settlement-operator-1",
                    "operatorRole" to "SETTLEMENT_OPERATOR",
                    "reason" to "exercise event-frontier rollback",
                    "voidedAt" to Instant.parse("2026-06-23T02:00:00Z"),
                    "createReplacement" to true,
                ),
            )
        }
        Mockito.reset(activationHandler)
        result.getOrNull()?.let { response ->
            assertThat(response.status).isEqualTo(409)
        }

        val original = getJson("/api/merchant-settlements/$originalId")
        assertThat(original.requiredText("status")).isEqualTo("PREPARED")
        assertThat(original["replacementSettlementId"].isNull).isTrue()
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from merchant_settlement where scope_identity = ?",
                Long::class.java,
                scopeIdentity,
            )
        ).isEqualTo(1L)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from merchant_settlement where effective_scope_identity = ?",
                Long::class.java,
                scopeIdentity,
            )
        ).isEqualTo(1L)
    }
    @Test
    fun `concurrent HTTP prepare converges on one effective settlement`() {
        val date = LocalDate.parse("2026-06-15")
        val payment = createSucceededPayment("B4-PREPARE-CONCURRENT", "100.00", "2026-06-15T02:00:00Z")
        reconcile(date, "statement-b4-prepare-concurrent", payments = listOf(payment to "100.00"))

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map { index ->
            executor.submit<HttpJsonResult> {
                ready.countDown()
                check(start.await(5, TimeUnit.SECONDS)) { "merchant settlement prepare requests were not released" }
                postJsonResult(
                    "/api/merchant-settlements",
                    mapOf(
                        "merchantId" to "M-001",
                        "channelId" to "C-001",
                        "currency" to "CNY",
                        "settlementDate" to date,
                        "requestedBy" to "settlement-concurrent-$index",
                        "requestedAt" to date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                    ),
                )
            }
        }.also {
            check(ready.await(5, TimeUnit.SECONDS)) { "merchant settlement prepare requests did not rendezvous" }
            start.countDown()
        }
        val results = try {
            futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertThat(results.map { it.status }.sorted()).containsExactly(201, 409)
        val successful = results.single { it.status == 201 }
        val effectiveId = successful.body.requiredText("settlementId")
        results.filter { it.status == 409 }.forEach {
            assertThat(it.body.requiredText("code")).isEqualTo("CONCURRENT_MODIFICATION")
        }
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from merchant_settlement where effective_scope_identity = ?",
                Long::class.java,
                "DAILY|M-001|C-001|CNY|$date",
            )
        ).isEqualTo(1L)
        val persisted = getJson("/api/merchant-settlements/$effectiveId")
        assertThat(persisted.requiredText("status")).isEqualTo("PREPARED")
        assertThat(persisted["lines"]).hasSize(1)
    }

    @Test
    fun `concurrent execution HTTP requests create one attempt and return stable conflict`() {
        val date = LocalDate.parse("2026-06-16")
        val payment = createSucceededPayment("B4-EXECUTION-CONCURRENT", "100.00", "2026-06-16T02:00:00Z")
        reconcile(date, "statement-b4-execution-concurrent", payments = listOf(payment to "100.00"))
        val settlementId = prepare(date, "b4-execution-concurrent").requiredText("settlementId")
        confirm(settlementId, "2026-06-17T02:00:00Z")

        val providerReady = CountDownLatch(2)
        Mockito.doAnswer { invocation ->
            providerReady.countDown()
            check(providerReady.await(5, TimeUnit.SECONDS)) { "settlement transfer calls did not rendezvous" }
            invocation.callRealMethod()
        }.`when`(transferHandler).call(mockitoAny())

        val requestReady = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map { index ->
            executor.submit<HttpJsonResult> {
                requestReady.countDown()
                check(start.await(5, TimeUnit.SECONDS)) { "settlement execution requests were not released" }
                postJsonResult(
                    "/api/merchant-settlements/$settlementId/executions",
                    mapOf(
                        "settlementId" to settlementId,
                        "operatorIdentity" to "settlement-concurrent-$index",
                        "operatorRole" to "SETTLEMENT_OPERATOR",
                        "requestedAt" to Instant.parse("2026-06-17T03:00:0${index}Z"),
                    ),
                )
            }
        }.also {
            check(requestReady.await(5, TimeUnit.SECONDS)) { "settlement execution requests did not rendezvous" }
            start.countDown()
        }
        val results = try {
            futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            Mockito.reset(transferHandler)
        }

        assertThat(results.map { it.status }.sorted()).containsExactly(200, 409)
        val conflict = results.single { it.status == 409 }.body
        assertThat(conflict.requiredText("code")).isEqualTo("CONCURRENT_MODIFICATION")
        val settlement = getJson("/api/merchant-settlements/$settlementId")
        assertThat(settlement.requiredText("status")).isEqualTo("PROCESSING")
        assertThat(settlement["attempts"]).hasSize(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from settlement_execution_attempt where merchant_settlement_id = ?",
                Long::class.java,
                settlementId,
            )
        ).isEqualTo(1L)
    }

    @Test
    fun `settlement execution database failure rolls back root and owned attempt together`() {
        val date = LocalDate.parse("2026-06-17")
        val payment = createSucceededPayment("B4-UOW-ROLLBACK", "100.00", "2026-06-17T02:00:00Z")
        reconcile(date, "statement-b4-uow-rollback", payments = listOf(payment to "100.00"))
        val settlementId = prepare(date, "b4-uow-rollback").requiredText("settlementId")
        confirm(settlementId, "2026-06-18T02:00:00Z")

        Mockito.doReturn(
            StartSettlementTransfer.Response(
                accepted = true,
                externalSettlementIdentity = "STL-" + "X".repeat(3_000),
                failureCode = null,
                diagnosticSummary = "force a database width violation after the aggregate was mutated",
            )
        ).`when`(transferHandler).call(mockitoAny())
        val request = runCatching {
            postJsonResult(
                "/api/merchant-settlements/$settlementId/executions",
                mapOf(
                    "settlementId" to settlementId,
                    "operatorIdentity" to "settlement-rollback",
                    "operatorRole" to "SETTLEMENT_OPERATOR",
                    "requestedAt" to Instant.parse("2026-06-18T03:00:00Z"),
                ),
            )
        }
        Mockito.reset(transferHandler)

        assertThat(request.isSuccess).isTrue()
        val failure = request.getOrThrow()
        assertThat(failure.status).isEqualTo(409)
        assertThat(failure.body.requiredText("code")).isEqualTo("CONCURRENT_MODIFICATION")
        val settlement = getJson("/api/merchant-settlements/$settlementId")
        assertThat(settlement.requiredText("status")).isEqualTo("CONFIRMED")
        assertThat(settlement["attempts"]).isEmpty()
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from settlement_execution_attempt where merchant_settlement_id = ?",
                Long::class.java,
                settlementId,
            )
        ).isEqualTo(0L)
    }

    private fun prepare(date: LocalDate, suffix: String): JsonNode = postJson(
        "/api/merchant-settlements",
        mapOf(
            "merchantId" to "M-001",
            "channelId" to "C-001",
            "currency" to "CNY",
            "settlementDate" to date,
            "requestedBy" to "settlement-requester-$suffix",
            "requestedAt" to date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
        ),
        expectedStatus = 201,
    )

    private fun confirm(settlementId: String, confirmedAt: String): JsonNode = postJson(
        "/api/merchant-settlements/$settlementId/confirmations",
        mapOf(
            "settlementId" to settlementId,
            "operatorIdentity" to "settlement-operator-1",
            "operatorRole" to "SETTLEMENT_OPERATOR",
            "confirmedAt" to Instant.parse(confirmedAt),
        ),
        expectedStatus = 200,
    )

    private fun startExecution(settlementId: String, requestedAt: String): JsonNode = postJson(
        "/api/merchant-settlements/$settlementId/executions",
        mapOf(
            "settlementId" to settlementId,
            "operatorIdentity" to "settlement-operator-1",
            "operatorRole" to "SETTLEMENT_OPERATOR",
            "requestedAt" to Instant.parse(requestedAt),
        ),
        expectedStatus = 200,
    )

    private fun reconcile(
        date: LocalDate,
        identity: String,
        payments: List<Pair<SucceededPayment, String>> = emptyList(),
        refunds: List<Pair<SucceededRefund, String>> = emptyList(),
    ) {
        val records = buildList {
            payments.forEachIndexed { index, (payment, amount) ->
                add(record("$identity-payment-$index", ReconciliationTransactionKind.PAYMENT, payment.channelTransactionId, amount, payment.occurredAt))
            }
            refunds.forEachIndexed { index, (refund, amount) ->
                add(record("$identity-refund-$index", ReconciliationTransactionKind.REFUND, refund.channelRefundId, amount, refund.occurredAt))
            }
        }
        statements.publish(statement(identity, "1", date, records))
        val triggeredAt = date.plusDays(1).atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant()
        val result = Mediator.commands.send(RunDailyReconciliationCmd.Request("C-001", "CNY", triggeredAt))
        assertThat(result.batchStatus).isEqualTo("COMPLETED")
        assertThat(result.unresolvedDifferenceCount).isZero()
    }

    private fun createSucceededPayment(prefix: String, amount: String, occurredAt: String): SucceededPayment {
        val created = postJson(
            "/api/payments",
            mapOf(
                "merchantId" to "M-001",
                "merchantOrderNumber" to "O-$prefix",
                "idempotencyKey" to "K-$prefix",
                "amount" to BigDecimal(amount),
                "currency" to "CNY",
                "paymentMethod" to "CARD",
                "expiresAt" to Instant.parse("2030-01-01T00:00:00Z"),
            ),
            expectedStatus = 201,
        )
        val paymentId = created.requiredText("paymentId")
        val attempt = postJson("/api/payments/$paymentId/attempts", emptyMap<String, Any>(), expectedStatus = 200)
        val transactionId = "CT-$prefix"
        postJson(
            "/api/channel/payment-results",
            mapOf(
                "channelId" to "C-001",
                "notificationId" to "N-$prefix",
                "paymentId" to paymentId,
                "paymentAttemptId" to attempt.requiredText("paymentAttemptId"),
                "channelTransactionId" to transactionId,
                "amount" to BigDecimal(amount),
                "currency" to "CNY",
                "result" to "SUCCESS",
                "occurredAt" to Instant.parse(occurredAt),
                "verificationMaterial" to "test-secret",
            ),
            expectedStatus = 200,
        )
        return SucceededPayment(paymentId, transactionId, occurredAt)
    }

    private fun createSucceededRefund(
        paymentId: String,
        merchantRefundNumber: String,
        amount: String,
        requestedAt: String,
        occurredAt: String,
    ): SucceededRefund {
        val created = postJson(
            "/api/refunds",
            mapOf(
                "merchantId" to "M-001",
                "merchantRefundNumber" to merchantRefundNumber,
                "paymentId" to paymentId,
                "amount" to BigDecimal(amount),
                "currency" to "CNY",
                "requestedAt" to Instant.parse(requestedAt),
            ),
            expectedStatus = 201,
        )
        val channelRefundId = "fake-refund-${created.requiredText("requestIdentity")}"
        postJson(
            "/api/channel/refund-results",
            mapOf(
                "channelId" to "C-001",
                "notificationId" to "N-$merchantRefundNumber",
                "refundId" to created.requiredText("refundId"),
                "refundAttemptId" to created.requiredText("refundAttemptId"),
                "channelRefundId" to channelRefundId,
                "amount" to BigDecimal(amount),
                "currency" to "CNY",
                "result" to "SUCCESS",
                "occurredAt" to Instant.parse(occurredAt),
                "verificationMaterial" to "test-secret",
            ),
            expectedStatus = 200,
        )
        return SucceededRefund(created.requiredText("refundId"), channelRefundId, occurredAt)
    }

    private fun statement(
        identity: String,
        revision: String,
        date: LocalDate,
        records: List<ChannelStatementRecord>,
    ) = ChannelStatement(
        channelId = "C-001",
        currency = "CNY",
        reconciliationDate = date,
        businessTimezone = "Asia/Shanghai",
        statementIdentity = identity,
        statementRevision = revision,
        completeness = StatementCompleteness.COMPLETE,
        fetchedAt = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
        records = records,
    )

    private fun record(
        identity: String,
        kind: ReconciliationTransactionKind,
        transactionIdentity: String,
        amount: String,
        occurredAt: String,
    ) = ChannelStatementRecord(
        recordIdentity = identity,
        transactionKind = kind,
        channelTransactionIdentity = transactionIdentity,
        amount = BigDecimal(amount),
        currency = "CNY",
        rawStatus = "SUCCEEDED",
        occurredAt = Instant.parse(occurredAt),
        receivedAt = Instant.parse(occurredAt).plusSeconds(30),
    )

    private fun settlementResultRequest(
        settlementId: String,
        attemptId: String,
        groupIdentity: String,
        requestIdentity: String,
        externalIdentity: String,
        notificationId: String,
        amount: String,
        result: String,
        occurredAt: String,
        receivedAt: String,
    ): Map<String, Any?> = mapOf(
        "channelId" to "C-001",
        "notificationId" to notificationId,
        "settlementId" to settlementId,
        "executionAttemptId" to attemptId,
        "executionGroupIdentity" to groupIdentity,
        "requestIdentity" to requestIdentity,
        "externalSettlementIdentity" to externalIdentity,
        "amount" to BigDecimal(amount),
        "currency" to "CNY",
        "result" to result,
        "resultCode" to result,
        "occurredAt" to Instant.parse(occurredAt),
        "receivedAt" to Instant.parse(receivedAt),
        "verificationMaterial" to "settlement-secret",
    )

    private fun awaitReliableEvent(
        eventIdentity: String,
        expectedState: Int,
        minimumTriedTimes: Int,
        retrySignal: (() -> Unit)? = null,
    ): ReliableEventRow {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var latest: ReliableEventRow? = null
        var pollCount = 0
        while (System.nanoTime() < deadline) {
            if (retrySignal != null && pollCount++ % 5 == 0) {
                retrySignal()
            }
            latest = jdbcTemplate.query(
                "select event_uuid, event_state, tried_times from __event where event_type = ? and data like ?",
                { resultSet, _ ->
                    ReliableEventRow(
                        eventUuid = resultSet.getString("event_uuid"),
                        state = resultSet.getInt("event_state"),
                        triedTimes = resultSet.getInt("tried_times"),
                    )
                },
                MerchantSettlementCompletedIntegrationEvent.EVENT_NAME,
                "%\"eventIdentity\":\"$eventIdentity\"%",
            ).firstOrNull()
            if (latest?.state == expectedState && latest.triedTimes >= minimumTriedTimes) {
                return latest
            }
            Thread.sleep(50)
        }
        error(
            "Timed out waiting for eventIdentity=$eventIdentity state=$expectedState " +
                "minimumTriedTimes=$minimumTriedTimes; latest=$latest"
        )
    }

    private fun awaitEventEnvelopes(
        receivedBodies: List<String>,
        eventIdentity: String,
        minimumCount: Int,
    ): List<IntegrationEventEnvelope> {
        val codec = IntegrationEventEnvelopeCodec()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var matching = emptyList<IntegrationEventEnvelope>()
        while (System.nanoTime() < deadline) {
            matching = receivedBodies.map(codec::decode)
                .filter { it.payloadJson.contains("\"eventIdentity\":\"$eventIdentity\"") }
            if (matching.size >= minimumCount) {
                return matching
            }
            Thread.sleep(50)
        }
        error("Timed out waiting for $minimumCount HTTP envelopes for eventIdentity=$eventIdentity; actual=${matching.size}")
    }

    private fun completedEventCount(settlementId: String): Long =
        jdbcTemplate.queryForObject(
            "select count(*) from __event where event_type = ? and data like ?",
            Long::class.java,
            "payment.merchant-settlement.completed.v1",
            "%\"settlementId\":\"$settlementId\"%",
        ) ?: 0L

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

    private fun postJsonResult(path: String, payload: Any): HttpJsonResult {
        val result = mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload))
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

    private fun JsonNode.arrayItem(field: String, value: String): JsonNode =
        elements().asSequence().firstOrNull { it.path(field).asText() == value }
            ?: error("missing array item $field=$value in $this")

    @Suppress("UNCHECKED_CAST")
    private fun <T> mockitoAny(): T {
        Mockito.any<T>()
        return null as T
    }

    private data class ReliableEventRow(
        val eventUuid: String,
        val state: Int,
        val triedTimes: Int,
    )

    private data class HttpJsonResult(
        val status: Int,
        val body: JsonNode,
    )

    private data class SucceededPayment(
        val paymentId: String,
        val channelTransactionId: String,
        val occurredAt: String,
    )

    private data class SucceededRefund(
        val refundId: String,
        val channelRefundId: String,
        val occurredAt: String,
    )
}
internal object EnqueueMerchantSettlementCompletedForTestCmd {
    @Service
    class Handler : CommandHandler<Request, Boolean> {
        override fun handle(command: Request): Boolean {
            Mediator.events.enqueue(command.event)
            return true
        }
    }

    data class Request(
        val event: MerchantSettlementCompletedIntegrationEvent,
    ) : Command<Boolean>
}