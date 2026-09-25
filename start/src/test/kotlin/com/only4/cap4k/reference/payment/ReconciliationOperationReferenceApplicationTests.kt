package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.channel.ChannelStatementFixtureStore
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorRegistry
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RunDailyReconciliationCmd
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatementRecord
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationOperationReferenceApplicationTests(
    @param:Autowired private val mvc: MockMvc,
    @param:Autowired private val json: ObjectMapper,
    @param:Autowired private val statements: ChannelStatementFixtureStore,
    @param:Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `PAY-AC-090 rerun replays accepted run without pulling newer statement`() {
        val date = LocalDate.parse("2026-07-03")
        publish(date, "1", "rerun")
        val initialRun = daily(date)
        publish(date, "2", "rerun")
        val path = "/api/reconciliation-runs/$initialRun/reruns"
        val key = "reconciliation-rerun-b15"
        val body = mapOf("idempotencyKey" to key, "requestedBy" to "body-spoof", "requestedAt" to "2000-01-01T00:00:00Z")
        val first = post(path, body, 200)
        assertThat(first.at("/receipt/acceptanceStatus").asText()).isEqualTo("ACCEPTED")
        assertThat(first.at("/receipt/resource/resourceType").asText()).isEqualTo("ReconciliationRun")
        assertThat(first.at("/receipt/readAfter/mode").asText()).isEqualTo("READ_ONCE")
        assertThat(count("operation", "idempotency_key", key)).isEqualTo(1L)
        publish(date, "3", "rerun")
        val replay = post(path, body, 200)
        assertThat(replay.path("runId").asText()).isEqualTo(first.path("runId").asText())
        assertReplay(first, replay)
        assertThat(count("reconciliation_run", "batch_id", batchId(initialRun))).isEqualTo(2L)
        val conflict = post("/api/reconciliation-runs/${first.path("runId").asText()}/reruns", body, 409)
        assertThat(conflict.path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT")
        assertThat(count("operation", "idempotency_key", key)).isEqualTo(1L)
        assertThat(count("reconciliation_run", "batch_id", batchId(initialRun))).isEqualTo(2L)
        val withoutActor = post(path, mapOf("idempotencyKey" to "$key-missing"), 200, actor = null)
        val unknownAliasIgnored = post(path, mapOf("idempotencyKey" to "$key-unknown"), 200, actor = "unknown-reference-alias")
        val unrelatedAliasIgnored = post(
            path,
            mapOf("idempotencyKey" to "$key-wrong-role"),
            200,
            actor = ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS,
        )
        assertThat(unknownAliasIgnored.path("runId").asText()).isEqualTo(withoutActor.path("runId").asText())
        assertThat(unrelatedAliasIgnored.path("runId").asText()).isEqualTo(withoutActor.path("runId").asText())
        assertThat(count("operation", "idempotency_key", "$key-missing")).isEqualTo(1L)
        assertThat(count("operation", "idempotency_key", "$key-unknown")).isEqualTo(1L)
        assertThat(count("operation", "idempotency_key", "$key-wrong-role")).isEqualTo(1L)
        assertThat(count("reconciliation_run", "batch_id", batchId(initialRun))).isEqualTo(3L)
    }

    @Test
    fun `PAY-AC-095 disposition replays one accountable decision and rejects conflict and missing actor`() {
        val date = LocalDate.parse("2026-07-04")
        publish(date, "1", "disposition")
        val runId = daily(date)
        val itemId = itemId(runId)
        val path = "/api/reconciliation-runs/$runId/differences/$itemId/dispositions"
        val key = "reconciliation-disposition-b15"
        val body = mapOf(
            "idempotencyKey" to key,
            "merchantId" to "M-001",
            "channelId" to "C-001",
            "conclusion" to "NO_SETTLEMENT_IMPACT",
            "settlementImpact" to "DOES_NOT_BLOCK_SETTLEMENT",
            "reason" to "reviewed channel evidence",
            "evidence" to "reference fixture evidence",
            "operatorIdentity" to "body-spoof",
            "operatorRole" to "VIEWER",
            "disposedAt" to "2000-01-01T00:00:00Z",
        )
        val first = post(path, body, 200)
        assertThat(first.path("actorId").asText()).isEqualTo("reference-reconciliation-operator")
        assertThat(first.at("/receipt/resource/resourceType").asText()).isEqualTo("DifferenceDisposition")
        assertThat(first.at("/receipt/readAfter/mode").asText()).isEqualTo("READ_ONCE")
        assertThat(jdbc.queryForObject("select operator_identity from reconciliation_disposition where id = ?", String::class.java, first.path("dispositionId").asText()))
            .isEqualTo("reference-reconciliation-operator")
        val replay = post(path, body, 200)
        assertThat(replay.path("dispositionId").asText()).isEqualTo(first.path("dispositionId").asText())
        assertThat(replay.path("decidedAt").asText()).isEqualTo(first.path("decidedAt").asText())
        assertReplay(first, replay)
        val conflict = post(path, body + ("evidence" to "a different evidence"), 409)
        assertThat(conflict.path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT")
        post(path, body + ("idempotencyKey" to "$key-missing"), 400, actor = null)
        post(
            path,
            body + ("idempotencyKey" to "$key-wrong-role"),
            400,
            actor = ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS,
        )
        assertThat(count("operation", "idempotency_key", key)).isEqualTo(1L)
        assertThat(count("operation", "idempotency_key", "$key-missing")).isZero()
        assertThat(count("operation", "idempotency_key", "$key-wrong-role")).isZero()
        assertThat(count("reconciliation_disposition", "reconciliation_item_id", itemId)).isEqualTo(1L)
    }

    @Test
    fun `PAY-AC-095 confirmation replays its fact without appending another disposition`() {
        val date = LocalDate.parse("2026-07-05")
        publish(date, "1", "confirmation")
        val runId = daily(date)
        val itemId = itemId(runId)
        val path = "/api/reconciliation-runs/$runId/differences/$itemId/confirmations"
        val key = "reconciliation-confirmation-b15"
        val body = mapOf(
            "idempotencyKey" to key,
            "merchantId" to "M-001",
            "channelId" to "C-001",
            "reason" to "channel-only success confirmed",
            "evidence" to "channel statement evidence",
            "operatorIdentity" to "body-spoof",
            "operatorRole" to "VIEWER",
            "recordedAt" to "2000-01-01T00:00:00Z",
        )
        post(path, body + ("idempotencyKey" to "$key-invalid") + ("channelId" to "C-OTHER"), 400)
        assertThat(count("operation", "idempotency_key", "$key-invalid")).isZero()
        assertThat(count("reconciliation_disposition", "reconciliation_item_id", itemId)).isZero()
        assertThat(count("reconciliation_confirmation_fact", "reconciliation_item_id", itemId)).isZero()
        val first = post(path, body, 200)
        assertThat(first.path("actorId").asText()).isEqualTo("reference-reconciliation-operator")
        assertThat(first.at("/receipt/resource/resourceType").asText()).isEqualTo("FactConfirmation")
        assertThat(first.at("/receipt/readAfter/mode").asText()).isEqualTo("READ_ONCE")
        assertThat(jdbc.queryForObject("select operator_identity from reconciliation_confirmation_fact where id = ?", String::class.java, first.path("confirmationFactId").asText()))
            .isEqualTo("reference-reconciliation-operator")
        val replay = post(path, body, 200)
        assertThat(replay.path("confirmationFactId").asText()).isEqualTo(first.path("confirmationFactId").asText())
        assertThat(replay.path("dispositionId").asText()).isEqualTo(first.path("dispositionId").asText())
        assertThat(replay.path("recordedAt").asText()).isEqualTo(first.path("recordedAt").asText())
        assertReplay(first, replay)
        val conflict = post(path, body + ("reason" to "another reason"), 409)
        assertThat(conflict.path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT")
        post(path, body + ("idempotencyKey" to "$key-missing"), 400, actor = null)
        post(
            path,
            body + ("idempotencyKey" to "$key-wrong-role"),
            400,
            actor = ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS,
        )
        assertThat(count("operation", "idempotency_key", key)).isEqualTo(1L)
        assertThat(count("operation", "idempotency_key", "$key-missing")).isZero()
        assertThat(count("operation", "idempotency_key", "$key-wrong-role")).isZero()
        assertThat(count("reconciliation_disposition", "reconciliation_item_id", itemId)).isEqualTo(1L)
        assertThat(count("reconciliation_confirmation_fact", "reconciliation_item_id", itemId)).isEqualTo(1L)
    }

    @Test
    fun `PAY-AC-090 provider synchronous failure does not claim accepted operation`() {
        val date = LocalDate.parse("2026-07-06")
        publish(date, "1", "failure")
        val runId = daily(date)
        statements.clear()
        val key = "reconciliation-failure-b15"
        val failed = post("/api/reconciliation-runs/$runId/reruns", mapOf("idempotencyKey" to key), 409)
        assertThat(failed.path("code").asText()).isEqualTo("STATEMENT_UNAVAILABLE")
        assertThat(count("operation", "idempotency_key", key)).isZero()
        assertThat(count("reconciliation_run", "batch_id", batchId(runId))).isEqualTo(1L)
        publish(date, "1", "failure")
    }

    private fun assertReplay(first: JsonNode, replay: JsonNode) {
        assertThat(replay.at("/receipt/operationId").asText()).isEqualTo(first.at("/receipt/operationId").asText())
        assertThat(replay.at("/receipt/acceptanceStatus").asText()).isEqualTo("ALREADY_ACCEPTED")
        assertThat(replay.at("/receipt/idempotentReplay").asBoolean()).isTrue()
    }

    private fun publish(date: LocalDate, revision: String, suffix: String) {
        val at = date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()
        statements.publish(
            ChannelStatement(
                channelId = "C-001",
                currency = "CNY",
                reconciliationDate = date,
                businessTimezone = "Asia/Shanghai",
                statementIdentity = "statement-b15-$suffix",
                statementRevision = revision,
                completeness = StatementCompleteness.COMPLETE,
                fetchedAt = at.plusSeconds(86400),
                records = listOf(
                    ChannelStatementRecord(
                        recordIdentity = "record-b15-$suffix-$revision",
                        transactionKind = ReconciliationTransactionKind.PAYMENT,
                        channelTransactionIdentity = "transaction-b15-$suffix",
                        amount = BigDecimal("42.00"),
                        currency = "CNY",
                        rawStatus = "SUCCEEDED",
                        occurredAt = at,
                        receivedAt = at.plusSeconds(30),
                    ),
                ),
            ),
        )
    }

    private fun daily(date: LocalDate): String = requireNotNull(
        Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = "C-001",
                currency = "CNY",
                triggeredAt = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().plusSeconds(3600),
            ),
        ).runId,
    )

    private fun itemId(runId: String): String = requireNotNull(
        jdbc.queryForObject(
            "select id from reconciliation_item where reconciliation_run_id = ?",
            String::class.java,
            runId,
        ),
    )

    private fun batchId(runId: String): String = requireNotNull(
        jdbc.queryForObject("select batch_id from reconciliation_run where id = ?", String::class.java, runId),
    )

    private fun count(table: String, field: String, value: String): Long =
        jdbc.queryForObject("select count(*) from $table where $field = ?", Long::class.java, value) ?: 0L

    private fun post(
        path: String,
        payload: Any,
        expected: Int,
        actor: String? = ReferenceActorRegistry.RECONCILIATION_OPERATOR_ALIAS,
    ): JsonNode {
        val request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload))
        if (actor != null) request.header("X-Reference-Actor-Context", actor)
        val result = mvc.perform(request).andExpect(status().`is`(expected)).andReturn()
        return json.readTree(result.response.contentAsByteArray)
    }
}
