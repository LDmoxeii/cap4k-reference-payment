package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.transfer.StartSettlementTransferHandler
import com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.channel.ChannelStatementFixtureStore
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.lifecycle.ActivateMerchantSettlementCmd
import com.only4.cap4k.reference.payment.adapter.endpoints.payment.PaymentHttpErrorAdvice
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RunDailyReconciliationCmd
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.appendReconciliationRun
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatementRecord
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
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
class ReconciliationReferenceApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val objectMapper: ObjectMapper,
    @param:Autowired private val statements: ChannelStatementFixtureStore,
    @param:Autowired private val entityManager: EntityManager,
    @param:Autowired private val transactionManager: PlatformTransactionManager,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @field:MockitoSpyBean
    private lateinit var transferHandler: StartSettlementTransferHandler

    @field:MockitoSpyBean
    private lateinit var activationHandler: ActivateMerchantSettlementCmd.Handler

    @Test
    fun `daily reconciliation matches payment and refund facts and exposes one effective run`() {
        val payment = createSucceededPayment(
            prefix = "B3-MATCH-PAYMENT",
            amount = "150.00",
            occurredAt = "2026-08-19T02:00:00Z",
        )
        val refund = createSucceededRefund(
            paymentId = payment.paymentId,
            merchantRefundNumber = "R-B3-MATCH",
            amount = "30.00",
            requestedAt = "2026-08-19T03:00:00Z",
            occurredAt = "2026-08-19T04:00:00Z",
        )
        val reconciliationDate = LocalDate.parse("2026-08-19")
        statements.publish(
            statement(
                identity = "statement-b3-matched",
                revision = "1",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-payment",
                        kind = ReconciliationTransactionKind.PAYMENT,
                        transactionIdentity = payment.channelTransactionId,
                        amount = "150.00",
                        occurredAt = "2026-08-19T02:00:00Z",
                    ),
                    record(
                        identity = "record-b3-refund",
                        kind = ReconciliationTransactionKind.REFUND,
                        transactionIdentity = refund.channelRefundId,
                        amount = "30.00",
                        occurredAt = "2026-08-19T04:00:00Z",
                    ),
                ),
            )
        )

        val response = Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = Instant.parse("2026-08-20T04:00:00Z"),
            )
        )
        assertThat(response.idempotentReplay).isFalse()
        assertThat(response.batchStatus).isEqualTo("COMPLETED")
        assertThat(response.unresolvedDifferenceCount).isZero()

        val batch = getJson("/api/reconciliation-batches/${response.batchId}")
        assertThat(batch.requiredText("status")).isEqualTo("COMPLETED")
        assertThat(batch.requiredText("currentEffectiveRunId")).isEqualTo(response.runId)
        assertThat(batch["matchedCount"].asInt()).isEqualTo(2)
        assertThat(batch["differenceCount"].asInt()).isZero()
        assertThat(batch["unresolvedDifferenceCount"].asInt()).isZero()
        assertThat(batch["settlementBlocked"].asBoolean()).isFalse()
        assertThat(batch["runs"]).hasSize(1)

        val run = batch["runs"][0]
        assertThat(run.requiredText("statementIdentity")).isEqualTo("statement-b3-matched")
        assertThat(run.requiredText("statementRevision")).isEqualTo("1")
        assertThat(run.requiredText("statementCompleteness")).isEqualTo("COMPLETE")
        assertThat(run["channelRecordCount"].asInt()).isEqualTo(2)
        assertThat(run["platformFactCount"].asInt()).isEqualTo(2)
        assertThat(run["items"]).hasSize(2)

        val paymentItem = run["items"].arrayItem("transactionKind", "PAYMENT")
        assertThat(paymentItem.requiredText("differenceType")).isEqualTo("MATCHED")
        assertThat(paymentItem.requiredText("paymentId")).isEqualTo(payment.paymentId)
        assertThat(paymentItem.requiredText("channelTransactionIdentity")).isEqualTo(payment.channelTransactionId)
        assertThat(paymentItem["channelAmount"].decimalValue()).isEqualByComparingTo("150.00")
        assertThat(paymentItem["platformAmount"].decimalValue()).isEqualByComparingTo("150.00")

        val refundItem = run["items"].arrayItem("transactionKind", "REFUND")
        assertThat(refundItem.requiredText("differenceType")).isEqualTo("MATCHED")
        assertThat(refundItem.requiredText("refundId")).isEqualTo(refund.refundId)
        assertThat(refundItem.requiredText("channelTransactionIdentity")).isEqualTo(refund.channelRefundId)
        assertThat(refundItem["channelAmount"].decimalValue()).isEqualByComparingTo("30.00")
        assertThat(refundItem["platformAmount"].decimalValue()).isEqualByComparingTo("30.00")
    }

    @Test
    fun `unknown refund and successful channel statement form confirmation without rewriting original refund`() {
        val payment = createSucceededPayment(
            prefix = "B3-STATUS-PAYMENT",
            amount = "80.00",
            occurredAt = "2026-08-14T02:00:00Z",
        )
        val refund = createUnknownRefund(
            paymentId = payment.paymentId,
            merchantRefundNumber = "R-B3-STATUS-UNKNOWN",
            amount = "25.00",
            requestedAt = "2026-08-15T02:00:00Z",
            occurredAt = "2026-08-15T03:00:00Z",
        )
        val reconciliationDate = LocalDate.parse("2026-08-15")
        statements.publish(
            statement(
                identity = "statement-b3-status-mismatch",
                revision = "1",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-status-refund",
                        kind = ReconciliationTransactionKind.REFUND,
                        transactionIdentity = refund.channelRefundId,
                        amount = "25.00",
                        occurredAt = "2026-08-15T03:00:00Z",
                    )
                ),
            )
        )

        val response = Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = Instant.parse("2026-08-16T04:00:00Z"),
            )
        )
        assertThat(response.batchStatus).isEqualTo("AWAITING_DISPOSITION")

        var batch = getJson("/api/reconciliation-batches/${response.batchId}")
        val item = batch["runs"][0]["items"].arrayItem("differenceType", "STATUS_MISMATCH")
        assertThat(item.requiredText("refundId")).isEqualTo(refund.refundId)
        assertThat(item.requiredText("platformRawStatus")).isEqualTo("RESULT_UNKNOWN")
        assertThat(item.requiredText("channelRawStatus")).isEqualTo("SUCCEEDED")
        assertThat(item["platformAmount"].decimalValue()).isEqualByComparingTo("25.00")
        assertThat(item["channelAmount"].decimalValue()).isEqualByComparingTo("25.00")
        assertThat(item.requiredText("platformCurrency")).isEqualTo("CNY")
        assertThat(item.requiredText("channelCurrency")).isEqualTo("CNY")
        assertThat(item.requiredText("platformTransactionIdentity")).isEqualTo(refund.channelRefundId)
        assertThat(item.requiredText("channelTransactionIdentity")).isEqualTo(refund.channelRefundId)

        val confirmed = postJson(
            "/api/reconciliation-items/${item.requiredText("itemId")}/dispositions",
            dispositionRequest(
                batchId = response.batchId,
                itemId = item.requiredText("itemId"),
                operatorIdentity = "operator-status-1",
                operatorRole = "RECONCILIATION_OPERATOR",
                disposedAt = "2026-08-16T05:00:00Z",
                evidence = "verified channel refund success against the original pending platform record",
                followUp = "include the confirmation fact in the B4 settlement candidate view",
            ),
            expectedStatus = 200,
        )
        assertThat(confirmed.requiredText("authorization")).isEqualTo("AUTHORIZED")
        assertThat(confirmed.requiredText("status")).isEqualTo("APPLIED")
        assertThat(confirmed.requiredText("confirmationFactId")).isNotBlank()
        assertThat(confirmed.requiredText("batchStatus")).isEqualTo("COMPLETED")

        batch = getJson("/api/reconciliation-batches/${response.batchId}")
        val confirmedItem = batch["runs"][0]["items"].arrayItem("differenceType", "STATUS_MISMATCH")
        assertThat(confirmedItem.requiredText("platformRawStatus")).isEqualTo("RESULT_UNKNOWN")
        assertThat(confirmedItem.requiredText("channelRawStatus")).isEqualTo("SUCCEEDED")
        assertThat(confirmedItem["dispositions"]).hasSize(1)
        assertThat(confirmedItem["confirmationFacts"]).hasSize(1)
        val disposition = confirmedItem["dispositions"][0]
        assertThat(disposition.requiredText("operatorIdentity")).isEqualTo("operator-status-1")
        assertThat(disposition.requiredText("evidence")).contains("original pending platform record")
        assertThat(disposition.requiredText("disposedAt")).isEqualTo("2026-08-16T05:00:00Z")
        val confirmation = confirmedItem["confirmationFacts"][0]
        assertThat(confirmation.requiredText("operatorIdentity")).isEqualTo("operator-status-1")
        assertThat(confirmation.requiredText("externalTransactionIdentity")).isEqualTo(refund.channelRefundId)
        assertThat(confirmation.requiredText("refundId")).isEqualTo(refund.refundId)
        assertThat(confirmation.requiredText("confirmedAt")).isEqualTo("2026-08-16T05:00:00Z")

        val originalRefund = getJson("/api/refunds/${refund.refundId}")
        assertThat(originalRefund.requiredText("status")).isEqualTo("RESULT_UNKNOWN")
        assertThat(originalRefund.requiredText("channelRefundId")).isEqualTo(refund.channelRefundId)
        assertThat(originalRefund["attempts"][0].requiredText("status")).isEqualTo("RESULT_UNKNOWN")
        assertThat(originalRefund["attempts"][0].requiredText("resultOccurredAt")).isEqualTo("2026-08-15T03:00:00Z")
    }

    @Test
    fun `authorized amount mismatch disposition preserves both amounts and appends an independent conclusion`() {
        val payment = createSucceededPayment(
            prefix = "B3-AMOUNT-MISMATCH",
            amount = "100.00",
            occurredAt = "2026-08-09T02:00:00Z",
        )
        val reconciliationDate = LocalDate.parse("2026-08-09")
        statements.publish(
            statement(
                identity = "statement-b3-amount-mismatch",
                revision = "1",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-amount-mismatch",
                        kind = ReconciliationTransactionKind.PAYMENT,
                        transactionIdentity = payment.channelTransactionId,
                        amount = "99.00",
                        occurredAt = "2026-08-09T02:00:00Z",
                    )
                ),
            )
        )

        val response = Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = Instant.parse("2026-08-10T04:00:00Z"),
            )
        )
        var batch = getJson("/api/reconciliation-batches/${response.batchId}")
        val item = batch["runs"][0]["items"].arrayItem("differenceType", "AMOUNT_MISMATCH")
        assertThat(item["platformAmount"].decimalValue()).isEqualByComparingTo("100.00")
        assertThat(item["channelAmount"].decimalValue()).isEqualByComparingTo("99.00")
        assertThat(item["settlementBlocked"].asBoolean()).isTrue()

        val disposed = postJson(
            "/api/reconciliation-items/${item.requiredText("itemId")}/dispositions",
            dispositionRequest(
                batchId = response.batchId,
                itemId = item.requiredText("itemId"),
                operatorIdentity = "operator-amount-1",
                operatorRole = "RECONCILIATION_OPERATOR",
                disposedAt = "2026-08-10T05:00:00Z",
                conclusion = "NO_SETTLEMENT_IMPACT",
                settlementImpact = "DOES_NOT_BLOCK_SETTLEMENT",
                evidence = "the channel fee presentation explains the one yuan statement delta",
                followUp = "retain both source amounts for audit",
            ),
            expectedStatus = 200,
        )
        assertThat(disposed.requiredText("status")).isEqualTo("APPLIED")
        assertThat(disposed["confirmationFactId"].isNull).isTrue()
        assertThat(disposed.requiredText("batchStatus")).isEqualTo("COMPLETED")

        batch = getJson("/api/reconciliation-batches/${response.batchId}")
        val disposedItem = batch["runs"][0]["items"].arrayItem("differenceType", "AMOUNT_MISMATCH")
        assertThat(disposedItem["platformAmount"].decimalValue()).isEqualByComparingTo("100.00")
        assertThat(disposedItem["channelAmount"].decimalValue()).isEqualByComparingTo("99.00")
        assertThat(disposedItem.requiredText("differenceType")).isEqualTo("AMOUNT_MISMATCH")
        assertThat(disposedItem["confirmationFacts"]).isEmpty()
        assertThat(disposedItem["dispositions"]).hasSize(1)
        val disposition = disposedItem["dispositions"][0]
        assertThat(disposition.requiredText("operatorIdentity")).isEqualTo("operator-amount-1")
        assertThat(disposition.requiredText("conclusion")).isEqualTo("NO_SETTLEMENT_IMPACT")
        assertThat(disposition.requiredText("settlementImpact")).isEqualTo("DOES_NOT_BLOCK_SETTLEMENT")
        assertThat(disposition.requiredText("disposedAt")).isEqualTo("2026-08-10T05:00:00Z")
        assertThat(disposition.requiredText("evidence")).contains("one yuan")
    }

    @Test
    fun `Asia Shanghai business day boundary separates 2359 and 0001 while preserving instants`() {
        val beforeMidnight = createSucceededPayment(
            prefix = "B3-TZ-BEFORE",
            amount = "10.00",
            occurredAt = "2026-08-10T15:59:00Z",
        )
        val afterMidnight = createSucceededPayment(
            prefix = "B3-TZ-AFTER",
            amount = "11.00",
            occurredAt = "2026-08-10T16:01:00Z",
        )
        statements.publish(
            statement(
                identity = "statement-b3-timezone-0810",
                revision = "1",
                date = LocalDate.parse("2026-08-10"),
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-timezone-before",
                        kind = ReconciliationTransactionKind.PAYMENT,
                        transactionIdentity = beforeMidnight.channelTransactionId,
                        amount = "10.00",
                        occurredAt = "2026-08-10T15:59:00Z",
                    )
                ),
            )
        )
        statements.publish(
            statement(
                identity = "statement-b3-timezone-0811",
                revision = "1",
                date = LocalDate.parse("2026-08-11"),
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-timezone-after",
                        kind = ReconciliationTransactionKind.PAYMENT,
                        transactionIdentity = afterMidnight.channelTransactionId,
                        amount = "11.00",
                        occurredAt = "2026-08-10T16:01:00Z",
                    )
                ),
            )
        )

        val first = Mediator.commands.send(
            RunDailyReconciliationCmd.Request("C-001", "CNY", Instant.parse("2026-08-11T04:00:00Z"))
        )
        val second = Mediator.commands.send(
            RunDailyReconciliationCmd.Request("C-001", "CNY", Instant.parse("2026-08-12T04:00:00Z"))
        )
        val firstBatch = getJson("/api/reconciliation-batches/${first.batchId}")
        val secondBatch = getJson("/api/reconciliation-batches/${second.batchId}")
        assertThat(firstBatch.requiredText("reconciliationDate")).isEqualTo("2026-08-10")
        assertThat(secondBatch.requiredText("reconciliationDate")).isEqualTo("2026-08-11")
        assertThat(firstBatch.requiredText("businessTimezone")).isEqualTo("Asia/Shanghai")
        assertThat(secondBatch.requiredText("businessTimezone")).isEqualTo("Asia/Shanghai")
        val firstItem = firstBatch["runs"][0]["items"].arrayItem("differenceType", "MATCHED")
        val secondItem = secondBatch["runs"][0]["items"].arrayItem("differenceType", "MATCHED")
        assertThat(firstItem.requiredText("paymentId")).isEqualTo(beforeMidnight.paymentId)
        assertThat(secondItem.requiredText("paymentId")).isEqualTo(afterMidnight.paymentId)
        assertThat(firstItem.requiredText("platformOccurredAt")).isEqualTo("2026-08-10T15:59:00Z")
        assertThat(firstItem.requiredText("channelOccurredAt")).isEqualTo("2026-08-10T15:59:00Z")
        assertThat(secondItem.requiredText("platformOccurredAt")).isEqualTo("2026-08-10T16:01:00Z")
        assertThat(secondItem.requiredText("channelOccurredAt")).isEqualTo("2026-08-10T16:01:00Z")
        assertThat(firstBatch["runs"][0]["platformFactCount"].asInt()).isEqualTo(1)
        assertThat(secondBatch["runs"][0]["platformFactCount"].asInt()).isEqualTo(1)
    }

    @Test
    fun `statement replay revision history and disposition preserve immutable evidence`() {
        val reconciliationDate = LocalDate.parse("2026-08-18")
        statements.publish(
            statement(
                identity = "statement-b3-difference",
                revision = "1",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-channel-only-r1",
                        kind = ReconciliationTransactionKind.PAYMENT,
                        transactionIdentity = "CT-B3-CHANNEL-ONLY",
                        amount = "42.00",
                        occurredAt = "2026-08-18T02:00:00Z",
                    )
                ),
            )
        )
        val created = Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = Instant.parse("2026-08-19T04:00:00Z"),
            )
        )
        assertThat(created.batchStatus).isEqualTo("AWAITING_DISPOSITION")
        var batch = getJson("/api/reconciliation-batches/${created.batchId}")
        val firstItem = batch["runs"][0]["items"].arrayItem("differenceType", "CHANNEL_ONLY")

        val denied = postJson(
            "/api/reconciliation-items/${firstItem.requiredText("itemId")}/dispositions",
            dispositionRequest(
                batchId = created.batchId,
                itemId = firstItem.requiredText("itemId"),
                operatorIdentity = "viewer-1",
                operatorRole = "VIEWER",
                disposedAt = "2026-08-19T05:00:00Z",
            ),
            expectedStatus = 200,
        )
        assertThat(denied.requiredText("authorization")).isEqualTo("DENIED")
        assertThat(denied.requiredText("status")).isEqualTo("REJECTED")
        assertThat(denied["confirmationFactId"].isNull).isTrue()
        assertThat(denied["settlementBlocked"].asBoolean()).isTrue()

        val replay = postJson(
            "/api/reconciliation-batches/${created.batchId}/reruns",
            rerunRequest(created.batchId, "2026-08-19T05:30:00Z"),
            expectedStatus = 200,
        )
        assertThat(replay["idempotentReplay"].asBoolean()).isTrue()
        assertThat(replay.requiredText("runId")).isEqualTo(created.runId)
        batch = getJson("/api/reconciliation-batches/${created.batchId}")
        assertThat(batch["runs"]).hasSize(1)
        assertThat(batch["runs"][0]["items"][0]["dispositions"]).hasSize(1)
        assertThat(batch["runs"][0]["items"][0]["confirmationFacts"]).isEmpty()

        statements.publish(
            statement(
                identity = "statement-b3-difference",
                revision = "2",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-channel-only-r2",
                        kind = ReconciliationTransactionKind.PAYMENT,
                        transactionIdentity = "CT-B3-CHANNEL-ONLY",
                        amount = "42.00",
                        occurredAt = "2026-08-18T02:00:00Z",
                    )
                ),
            )
        )
        val revised = postJson(
            "/api/reconciliation-batches/${created.batchId}/reruns",
            rerunRequest(created.batchId, "2026-08-19T06:00:00Z"),
            expectedStatus = 200,
        )
        assertThat(revised["idempotentReplay"].asBoolean()).isFalse()
        assertThat(revised.requiredText("statementRevision")).isEqualTo("2")
        assertThat(revised.requiredText("runId")).isNotEqualTo(created.runId)

        batch = getJson("/api/reconciliation-batches/${created.batchId}")
        assertThat(batch["runs"]).hasSize(2)
        assertThat(batch.requiredText("currentEffectiveRunId")).isEqualTo(revised.requiredText("runId"))
        val oldRun = batch["runs"].arrayItem("statementRevision", "1")
        val currentRun = batch["runs"].arrayItem("statementRevision", "2")
        assertThat(oldRun.requiredText("status")).isEqualTo("SUPERSEDED")
        assertThat(oldRun["items"][0]["dispositions"]).hasSize(1)
        assertThat(currentRun["items"][0]["dispositions"]).isEmpty()
        val currentItem = currentRun["items"].arrayItem("differenceType", "CHANNEL_ONLY")
        assertThat(currentItem["platformFactIdentity"].isNull).isTrue()
        assertThat(currentItem.requiredText("channelTransactionIdentity")).isEqualTo("CT-B3-CHANNEL-ONLY")

        val confirmed = postJson(
            "/api/reconciliation-items/${currentItem.requiredText("itemId")}/dispositions",
            dispositionRequest(
                batchId = created.batchId,
                itemId = currentItem.requiredText("itemId"),
                merchantId = "M-001",
                channelId = "C-001",
                operatorIdentity = "operator-1",
                operatorRole = "RECONCILIATION_OPERATOR",
                disposedAt = "2026-08-19T06:30:00Z",
            ),
            expectedStatus = 200,
        )
        assertThat(confirmed.requiredText("authorization")).isEqualTo("AUTHORIZED")
        assertThat(confirmed.requiredText("status")).isEqualTo("APPLIED")
        assertThat(confirmed.requiredText("confirmationFactId")).isNotBlank()
        assertThat(confirmed.requiredText("batchStatus")).isEqualTo("COMPLETED")
        assertThat(confirmed["settlementBlocked"].asBoolean()).isFalse()

        batch = getJson("/api/reconciliation-batches/${created.batchId}")
        val confirmedItem = batch["runs"].arrayItem("statementRevision", "2")["items"]
            .arrayItem("differenceType", "CHANNEL_ONLY")
        assertThat(confirmedItem["resolved"].asBoolean()).isTrue()
        assertThat(confirmedItem["settlementBlocked"].asBoolean()).isFalse()
        assertThat(confirmedItem["dispositions"]).hasSize(1)
        assertThat(confirmedItem["confirmationFacts"]).hasSize(1)
        val confirmation = confirmedItem["confirmationFacts"][0]
        assertThat(confirmation.requiredText("sourceDifferenceIdentity"))
            .isEqualTo(confirmedItem.requiredText("differenceIdentity"))
        assertThat(confirmation.requiredText("externalTransactionIdentity")).isEqualTo("CT-B3-CHANNEL-ONLY")
        assertThat(confirmation["amount"].decimalValue()).isEqualByComparingTo("42.00")
        assertThat(confirmation.requiredText("currency")).isEqualTo("CNY")
        assertThat(confirmedItem["platformFactIdentity"].isNull).isTrue()
    }

    @Test
    fun `unavailable and incomplete statements remain queryable and cannot complete`() {
        val response = Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = Instant.parse("2026-08-17T04:00:00Z"),
            )
        )
        assertThat(response.runId).isNull()
        assertThat(response.batchStatus).isEqualTo("FETCH_FAILED")
        assertThat(response.blockingReason).contains("unavailable")

        var batch = getJson("/api/reconciliation-batches/${response.batchId}")
        assertThat(batch.requiredText("status")).isEqualTo("FETCH_FAILED")
        assertThat(batch["settlementBlocked"].asBoolean()).isTrue()
        assertThat(batch["runs"]).isEmpty()

        statements.publish(
            statement(
                identity = "statement-b3-incomplete",
                revision = "1",
                date = LocalDate.parse("2026-08-16"),
                completeness = StatementCompleteness.INCOMPLETE,
                records = emptyList(),
            )
        )
        val rerun = postJson(
            "/api/reconciliation-batches/${response.batchId}/reruns",
            rerunRequest(response.batchId, "2026-08-18T04:00:00Z"),
            expectedStatus = 200,
        )
        assertThat(rerun.requiredText("status")).isEqualTo("REVIEW_REQUIRED")
        assertThat(rerun["idempotentReplay"].asBoolean()).isFalse()

        batch = getJson("/api/reconciliation-batches/${response.batchId}")
        assertThat(batch.requiredText("status")).isEqualTo("REVIEW_REQUIRED")
        assertThat(batch["settlementBlocked"].asBoolean()).isTrue()
        assertThat(batch.requiredText("blockingReason")).contains("not complete")
        assertThat(batch["runs"]).hasSize(1)
        assertThat(batch["runs"][0].requiredText("statementCompleteness")).isEqualTo("INCOMPLETE")
        assertThat(batch["runs"][0]["items"]).isEmpty()
    }

    @Test
    fun `concurrent scheduler commands keep one batch and one initial statement revision`() {
        val reconciliationDate = LocalDate.parse("2026-08-13")
        statements.publish(
            statement(
                identity = "statement-b3-scope-concurrency",
                revision = "1",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = emptyList(),
            )
        )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<RunDailyReconciliationCmd.Response>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                executor.submit {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "reconciliation commands were not released" }
                    try {
                        results += Mediator.commands.send(
                            RunDailyReconciliationCmd.Request(
                                channelId = "C-001",
                                currency = "CNY",
                                triggeredAt = Instant.parse("2026-08-14T04:00:00Z"),
                            )
                        )
                    } catch (error: Throwable) {
                        failures += error
                    }
                }
            }
            check(ready.await(5, TimeUnit.SECONDS)) { "reconciliation commands did not rendezvous" }
            start.countDown()
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertThat(results).isNotEmpty
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_batch where channel_id = ? and currency = ? and reconciliation_date = ?",
                Long::class.java,
                "C-001",
                "CNY",
                java.sql.Date.valueOf(reconciliationDate),
            )
        ).isEqualTo(1L)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_run r join reconciliation_batch b on b.id = r.batch_id " +
                    "where b.channel_id = ? and b.currency = ? and b.reconciliation_date = ? " +
                    "and r.statement_identity = ? and r.statement_revision = ?",
                Long::class.java,
                "C-001",
                "CNY",
                java.sql.Date.valueOf(reconciliationDate),
                "statement-b3-scope-concurrency",
                "1",
            )
        ).isEqualTo(1L)
        assertThat(failures.size).isLessThanOrEqualTo(1)
    }

    @Test
    fun `two real transactions cannot append the same statement revision twice`() {
        val reconciliationDate = LocalDate.parse("2026-08-12")
        statements.publish(
            statement(
                identity = "statement-b3-revision-concurrency",
                revision = "1",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = emptyList(),
            )
        )
        val created = Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = Instant.parse("2026-08-13T04:00:00Z"),
            )
        )
        val revisionTwo = statement(
            identity = "statement-b3-revision-concurrency",
            revision = "2",
            date = reconciliationDate,
            completeness = StatementCompleteness.COMPLETE,
            records = emptyList(),
        )
        val ready = CountDownLatch(2)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { index ->
                executor.submit {
                    try {
                        TransactionTemplate(transactionManager).executeWithoutResult {
                            val batch = requireNotNull(
                                entityManager.find(
                                    ReconciliationBatch::class.java,
                                    ReconciliationBatchId.parse(created.batchId),
                                )
                            )
                            ready.countDown()
                            check(ready.await(5, TimeUnit.SECONDS)) { "reconciliation workers did not rendezvous" }
                            batch.appendReconciliationRun(
                                statement = revisionTwo,
                                platformFacts = emptyList(),
                                startedAt = LocalDateTime.parse("2026-08-13T12:00:0$index"),
                            )
                            entityManager.flush()
                        }
                    } catch (error: Throwable) {
                        failures += error
                    }
                }
            }
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertThat(failures).hasSize(1)
        val failureMessages = failures.single().causalChain()
            .mapNotNull { it.message }
            .joinToString(" | ")
            .lowercase()
        assertThat(failureMessages).containsAnyOf("unique", "23505", "constraint")
        val mapped = PaymentHttpErrorAdvice().concurrentModification(
            DataIntegrityViolationException("same reconciliation statement revision", failures.single())
        )
        assertThat(mapped.statusCode.value()).isEqualTo(409)
        assertThat(mapped.body!!.code).isEqualTo("CONCURRENT_MODIFICATION")

        val batch = getJson("/api/reconciliation-batches/${created.batchId}")
        assertThat(batch["runs"]).hasSize(2)
        assertThat(batch["runs"].elements().asSequence().count {
            it.path("statementRevision").asText() == "2"
        }).isEqualTo(1)
        val effectiveRun = batch["runs"].arrayItem("runId", batch.requiredText("currentEffectiveRunId"))
        assertThat(effectiveRun.requiredText("statementRevision")).isEqualTo("2")
    }

    @Test
    fun `owned reconciliation graph rolls back when one child cannot be persisted`() {
        val reconciliationDate = LocalDate.parse("2026-08-11")
        statements.publish(
            statement(
                identity = "statement-b3-rollback-" + "X".repeat(3_000),
                revision = "1",
                date = reconciliationDate,
                completeness = StatementCompleteness.COMPLETE,
                records = listOf(
                    record(
                        identity = "record-b3-rollback",
                        kind = ReconciliationTransactionKind.PAYMENT,
                        transactionIdentity = "CT-B3-ROLLBACK",
                        amount = "10.00",
                        occurredAt = "2026-08-11T02:00:00Z",
                    )
                ),
            )
        )

        val failure = runCatching {
            Mediator.commands.send(
                RunDailyReconciliationCmd.Request(
                    channelId = "C-001",
                    currency = "CNY",
                    triggeredAt = Instant.parse("2026-08-12T04:00:00Z"),
                )
            )
        }.exceptionOrNull()
        assertThat(failure).isNotNull
        assertThat(failure!!.causalChain().mapNotNull { it.message }.joinToString(" | ").lowercase())
            .containsAnyOf("value too long", "22001", "data exception")
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_batch where channel_id = ? and currency = ? and reconciliation_date = ?",
                Long::class.java,
                "C-001",
                "CNY",
                java.sql.Date.valueOf(reconciliationDate),
            )
        ).isEqualTo(0L)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_run where statement_revision = ? and statement_identity like ?",
                Long::class.java,
                "1",
                "statement-b3-rollback-%",
            )
        ).isEqualTo(0L)
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_item where channel_transaction_identity = ?",
                Long::class.java,
                "CT-B3-ROLLBACK",
            )
        ).isEqualTo(0L)
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
        val attempt = postJson(
            "/api/payments/$paymentId/attempts",
            emptyMap<String, Any>(),
            expectedStatus = 200,
        )
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
        return SucceededPayment(paymentId, transactionId)
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
        return SucceededRefund(created.requiredText("refundId"), channelRefundId)
    }

    private fun createUnknownRefund(
        paymentId: String,
        merchantRefundNumber: String,
        amount: String,
        requestedAt: String,
        occurredAt: String,
    ): ChannelRefund {
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
                "result" to "UNKNOWN",
                "occurredAt" to Instant.parse(occurredAt),
                "verificationMaterial" to "test-secret",
            ),
            expectedStatus = 200,
        )
        return ChannelRefund(created.requiredText("refundId"), channelRefundId)
    }

    private fun statement(
        identity: String,
        revision: String,
        date: LocalDate,
        completeness: StatementCompleteness,
        records: List<ChannelStatementRecord>,
    ) = ChannelStatement(
        channelId = "C-001",
        currency = "CNY",
        reconciliationDate = date,
        businessTimezone = "Asia/Shanghai",
        statementIdentity = identity,
        statementRevision = revision,
        completeness = completeness,
        fetchedAt = date.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
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

    private fun rerunRequest(batchId: String, requestedAt: String): Map<String, Any> = mapOf(
        "batchId" to batchId,
        "requestedBy" to "operator-1",
        "requestedAt" to Instant.parse(requestedAt),
    )

    private fun dispositionRequest(
        batchId: String,
        itemId: String,
        merchantId: String? = null,
        channelId: String? = null,
        operatorIdentity: String,
        operatorRole: String,
        disposedAt: String,
        conclusion: String = "CONFIRM_PLATFORM_FACT",
        settlementImpact: String = "CONFIRMS_SETTLEMENT_FACT",
        evidence: String = "channel statement and operator review",
        followUp: String? = "include in settlement candidate projection",
    ): Map<String, Any?> = mapOf(
        "batchId" to batchId,
        "itemId" to itemId,
        "merchantId" to merchantId,
        "channelId" to channelId,
        "operatorIdentity" to operatorIdentity,
        "operatorRole" to operatorRole,
        "conclusion" to conclusion,
        "settlementImpact" to settlementImpact,
        "evidence" to evidence,
        "followUp" to followUp,
        "disposedAt" to Instant.parse(disposedAt),
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

    private fun JsonNode.arrayItem(field: String, value: String): JsonNode =
        elements().asSequence().firstOrNull { it.path(field).asText() == value }
            ?: error("missing array item $field=$value in $this")

    private fun Throwable.causalChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private data class SucceededPayment(
        val paymentId: String,
        val channelTransactionId: String,
    )

    private data class SucceededRefund(
        val refundId: String,
        val channelRefundId: String,
    )

    private data class ChannelRefund(
        val refundId: String,
        val channelRefundId: String,
    )
}
