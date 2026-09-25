package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Black-box Spring tests for the reference-only bill fixture and the public ReconciliationRun
 * resource.  The fixture registers immutable provider evidence; every run in these tests is
 * then discovered by the normal provider pull + CAP4K command/UoW path.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthoritativeBillReferenceApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val objectMapper: ObjectMapper,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    @DisplayName("PAY-AC-087/094 — 权威 revision 重放、冲突和迟到版本保持单调")
    fun `bill revision is immutable idempotent and monotonic`() {
        val scope = nextScope("revision")
        val revisionTwo = registerBill(scope, revision = "2", fingerprint = "${scope.billIdentity}-fp-2")
        assertThat(revisionTwo.requiredText("currentRevision")).isEqualTo("2")
        assertThat(revisionTwo["idempotentReplay"].asBoolean()).isFalse()
        assertThat(revisionTwo["becameCurrent"].asBoolean()).isTrue()

        val replay = registerBill(scope, revision = "2", fingerprint = "${scope.billIdentity}-fp-2")
        assertThat(replay.requiredText("billId")).isEqualTo(revisionTwo.requiredText("billId"))
        assertThat(replay["idempotentReplay"].asBoolean()).isTrue()
        assertThat(replay["becameCurrent"].asBoolean()).isFalse()

        val conflict = postJsonResult(
            "/api/reference-fixtures/bills",
            billRequest(scope, revision = "2", fingerprint = "${scope.billIdentity}-conflicting-fingerprint"),
        )
        assertThat(conflict.status).isEqualTo(400)
        assertThat(conflict.body.requiredText("code")).isEqualTo("VALIDATION_ERROR")

        val lateOlder = registerBill(scope, revision = "1", fingerprint = "${scope.billIdentity}-fp-1")
        assertThat(lateOlder.requiredText("currentRevision")).isEqualTo("2")
        assertThat(lateOlder["idempotentReplay"].asBoolean()).isFalse()
        assertThat(lateOlder["becameCurrent"].asBoolean()).isFalse()
        assertThat(count("select count(*) from bill_revision where authoritative_bill_id = ?", revisionTwo.requiredText("billId"))).isEqualTo(2)
    }

    @Test
    @DisplayName("PAY-AC-087 — 相同 bill signal 可诊断重试，恢复后仅形成一个有效 run")
    fun `temporarily unavailable bill signal retries exactly its stable signal identity`() {
        val scope = nextScope("retry")
        registerBill(
            scope,
            revision = "1",
            fingerprint = "${scope.billIdentity}-fp-1",
            unavailableReadCount = 1,
        )

        val first = signal(scope, revision = "1", signalIdentity = "${scope.billIdentity}-signal")
        assertThat(first.requiredText("runStatus")).isEqualTo("FAILED")
        assertThat(first["runId"].isNull).isTrue()
        assertThat(first.requiredText("diagnostic")).contains("暂不可读")
        assertThat(runCount(scope)).isZero()
        assertThat(readAttempts(scope)).isEqualTo(1)
        assertThat(signalReadAttempts(scope, "${scope.billIdentity}-signal")).isEqualTo(1)

        val recovered = signal(scope, revision = "1", signalIdentity = "${scope.billIdentity}-signal")
        val runId = recovered.requiredText("runId")
        assertThat(recovered.requiredText("runStatus")).isEqualTo("COMPLETED")
        assertThat(recovered["idempotentReplay"].asBoolean()).isFalse()
        assertThat(runCount(scope)).isEqualTo(1)
        assertThat(readAttempts(scope)).isEqualTo(2)
        assertThat(signalReadAttempts(scope, "${scope.billIdentity}-signal")).isEqualTo(2)

        val replay = signal(scope, revision = "1", signalIdentity = "${scope.billIdentity}-signal")
        assertThat(replay.requiredText("runId")).isEqualTo(runId)
        assertThat(replay["idempotentReplay"].asBoolean()).isTrue()
        assertThat(runCount(scope)).isEqualTo(1)
        assertThat(signalReadAttempts(scope, "${scope.billIdentity}-signal")).isEqualTo(3)
    }

    @Test
    @DisplayName("PAY-AC-087 — signal 只重试明确暂不可读，不能吞掉 revision 业务冲突")
    fun `bill signal propagates a revision contradiction instead of recording a retryable failure`() {
        val scope = nextScope("signal-conflict")
        registerBill(scope, revision = "1", fingerprint = "${scope.billIdentity}-fp-1")

        val conflict = postJsonResult(
            "/api/reference-fixtures/bills/${scope.billIdentity}/signals",
            mapOf(
                "channelId" to scope.channelId,
                "businessDate" to scope.businessDate,
                "currency" to scope.currency,
                "businessTimezone" to scope.timezone,
                "signalIdentity" to "${scope.billIdentity}-signal",
                "announcedRevision" to "2",
                "publishedAt" to scope.publishedAt,
            ),
        )
        assertThat(conflict.status).isEqualTo(400)
        assertThat(conflict.body.requiredText("code")).isEqualTo("VALIDATION_ERROR")
        assertThat(runCount(scope)).isZero()
        assertThat(count(
            "select count(*) from bill_available_signal s join authoritative_bill b on b.id = s.authoritative_bill_id " +
                "where b.channel_id = ? and b.bill_identity = ?",
            scope.channelId,
            scope.billIdentity,
        )).isZero()
    }

    @Test
    @DisplayName("PAY-AC-045/087/094 — 新 revision 追加 Run 历史，同 revision rerun 复用稳定运行")
    fun `new bill revision supersedes effective run while rerun of same revision reuses it`() {
        val scope = nextScope("history")
        registerBill(scope, revision = "1", fingerprint = "${scope.billIdentity}-fp-1")
        val firstRunId = signal(scope, revision = "1", signalIdentity = "${scope.billIdentity}-signal-1").requiredText("runId")

        registerBill(scope, revision = "2", fingerprint = "${scope.billIdentity}-fp-2")
        val secondRunId = signal(scope, revision = "2", signalIdentity = "${scope.billIdentity}-signal-2").requiredText("runId")
        assertThat(secondRunId).isNotEqualTo(firstRunId)

        val historic = getJson("/api/reconciliation-runs/$firstRunId")
        val effective = getJson("/api/reconciliation-runs/$secondRunId")
        assertThat(historic["effectiveRun"].asBoolean()).isFalse()
        assertThat(historic.requiredText("status")).isEqualTo("SUPERSEDED")
        assertThat(effective["effectiveRun"].asBoolean()).isTrue()
        assertThat(effective.requiredText("billRevision")).isEqualTo("2")

        val rerun = postJson(
            "/api/reconciliation-runs/$secondRunId/reruns",
            mapOf("idempotencyKey" to "${scope.billIdentity}-rerun"),
            headers = actorHeader(),
        )
        assertThat(rerun.requiredText("runId")).isEqualTo(secondRunId)
        assertThat(rerun["idempotentReplay"].asBoolean()).isTrue()
        assertThat(runCount(scope)).isEqualTo(2)
        assertThat(count("select count(*) from reconciliation_batch where current_effective_run_id = ?", secondRunId)).isEqualTo(1)
    }

    @Test
    @DisplayName("PAY-AC-093 — ReconciliationRun 权威列表使用受筛选约束的稳定 keyset cursor")
    fun `reconciliation run list pages by immutable sort key and rejects invalid cursor reuse`() {
        val scope = nextScope("page")
        var authoritativeBillId: String? = null
        (1..23).forEach { revision ->
            val registered = registerBill(scope, revision.toString(), "${scope.billIdentity}-fp-$revision")
            authoritativeBillId = authoritativeBillId ?: registered.requiredText("billId")
            signal(scope, revision.toString(), "${scope.billIdentity}-signal-$revision")
        }

        val filters = mapOf(
            "merchantId" to "",
            "channelId" to scope.channelId,
            "currency" to scope.currency,
            "businessDate" to scope.businessDate,
            "billId" to requireNotNull(authoritativeBillId),
        )
        val first = postJson("/api/reconciliation-runs/search", filters)
        assertThat(first["pageSize"].asInt()).isEqualTo(20)
        assertThat(first["items"]).hasSize(20)
        val cursor = first.requiredText("nextCursor")
        assertDescending(first["items"])

        val second = postJson("/api/reconciliation-runs/search", filters + mapOf("cursor" to cursor, "pageSize" to 100))
        assertThat(second["pageSize"].asInt()).isEqualTo(100)
        assertThat(second["items"]).hasSize(3)
        assertDescending(second["items"])
        assertThat((first["items"] + second["items"]).map { it.requiredText("billId") })
            .containsOnly(requireNotNull(authoritativeBillId))
        val firstIds = first["items"].map { it.requiredText("runId") }.toSet()
        assertThat(second["items"].map { it.requiredText("runId") }).doesNotContainAnyElementsOf(firstIds)

        val crossFilter = postJsonResult(
            "/api/reconciliation-runs/search",
            filters + mapOf("currency" to "USD", "cursor" to cursor),
        )
        assertInvalidCursor(crossFilter)
        val malformed = postJsonResult("/api/reconciliation-runs/search", filters + mapOf("cursor" to "definitely-not-a-cursor"))
        assertInvalidCursor(malformed)
        val tooLarge = postJsonResult("/api/reconciliation-runs/search", filters + mapOf("pageSize" to 101))
        assertThat(tooLarge.status).isEqualTo(400)
        assertThat(tooLarge.body.requiredText("code")).isEqualTo("VALIDATION_ERROR")
    }

    @Test
    @DisplayName("PAY-AC-044/047/086/095 — 系统重跑不依赖 actor，人工差异动作强制可信 actor context")
    fun `reconciliation rerun uses system actor while accountable commands reject untrusted actor`() {
        val scope = nextScope("actor")
        registerBill(
            scope,
            revision = "1",
            fingerprint = "${scope.billIdentity}-fp-1",
            records = listOf(channelOnlyRecord(scope)),
        )
        val runId = signal(scope, revision = "1", signalIdentity = "${scope.billIdentity}-signal").requiredText("runId")
        val run = getJson("/api/reconciliation-runs/$runId")
        val itemId = run["differences"][0].requiredText("itemId")
        val disposition = mapOf(
            "merchantId" to "M-ACTOR-${scope.sequence}",
            "channelId" to scope.channelId,
            "conclusion" to "CONFIRM_PLATFORM_FACT",
            "settlementImpact" to "CONFIRMS_SETTLEMENT_FACT",
            "reason" to "可信账单已核验",
            "evidence" to "evidence://${scope.billIdentity}/1",
            "idempotencyKey" to "${scope.billIdentity}-disposition",
        )
        val confirmation = mapOf(
            "merchantId" to "M-ACTOR-${scope.sequence}",
            "channelId" to scope.channelId,
            "reason" to "可信账单补录确认",
            "evidence" to "evidence://${scope.billIdentity}/confirmation",
            "idempotencyKey" to "${scope.billIdentity}-confirmation",
        )
        val rerunWithoutActor = postJson(
            "/api/reconciliation-runs/$runId/reruns",
            mapOf("idempotencyKey" to "${scope.billIdentity}-system-rerun"),
        )
        val rerunWithUnknownActor = postJson(
            "/api/reconciliation-runs/$runId/reruns",
            mapOf("idempotencyKey" to "${scope.billIdentity}-system-rerun-unknown-header"),
            headers = unknownActorHeader(),
        )
        assertThat(rerunWithoutActor.requiredText("runId")).isEqualTo(rerunWithUnknownActor.requiredText("runId"))
        assertThat(rerunWithoutActor.at("/receipt/acceptanceStatus").asText()).isEqualTo("ACCEPTED")
        assertThat(rerunWithUnknownActor.at("/receipt/acceptanceStatus").asText()).isEqualTo("ACCEPTED")

        val baselineRuns = runCount(scope)
        val baselineDispositions = count("select count(*) from reconciliation_disposition")
        val baselineConfirmations = count("select count(*) from reconciliation_confirmation_fact")

        assertValidationError(postJsonResult("/api/reconciliation-runs/$runId/differences/$itemId/dispositions", disposition))
        assertValidationError(postJsonResult(
            "/api/reconciliation-runs/$runId/differences/$itemId/dispositions", disposition, unknownActorHeader(),
        ))
        assertValidationError(postJsonResult("/api/reconciliation-runs/$runId/differences/$itemId/confirmations", confirmation))
        assertValidationError(postJsonResult(
            "/api/reconciliation-runs/$runId/differences/$itemId/confirmations", confirmation, unknownActorHeader(),
        ))
        assertThat(runCount(scope)).isEqualTo(baselineRuns)
        assertThat(count("select count(*) from reconciliation_disposition")).isEqualTo(baselineDispositions)
        assertThat(count("select count(*) from reconciliation_confirmation_fact")).isEqualTo(baselineConfirmations)

        val applied = postJson(
            "/api/reconciliation-runs/$runId/differences/$itemId/confirmations",
            confirmation,
            headers = actorHeader(),
        )
        assertThat(applied.requiredText("actorId")).isEqualTo("reference-reconciliation-operator")
        assertThat(applied.requiredText("reason")).isEqualTo("可信账单补录确认")
        assertThat(applied.requiredText("evidence")).isEqualTo("evidence://${scope.billIdentity}/confirmation")
        assertThat(applied.requiredText("recordedAt")).isNotBlank()
        val updated = getJson("/api/reconciliation-runs/$runId")
        val updatedItem = updated["differences"][0]
        assertThat(updatedItem["confirmationFacts"][0].requiredText("operatorIdentity"))
            .isEqualTo("reference-reconciliation-operator")
        assertThat(updatedItem["confirmationFacts"][0].requiredText("evidence"))
            .isEqualTo("evidence://${scope.billIdentity}/confirmation")
    }

    private fun registerBill(
        scope: Scope,
        revision: String,
        fingerprint: String,
        unavailableReadCount: Int? = null,
        records: List<Map<String, Any>> = emptyList(),
    ): JsonNode = postJson(
        "/api/reference-fixtures/bills",
        billRequest(scope, revision, fingerprint, unavailableReadCount, records),
    )

    private fun signal(scope: Scope, revision: String, signalIdentity: String): JsonNode = postJson(
        "/api/reference-fixtures/bills/${scope.billIdentity}/signals",
        mapOf(
            "channelId" to scope.channelId,
            "businessDate" to scope.businessDate,
            "currency" to scope.currency,
            "businessTimezone" to scope.timezone,
            "signalIdentity" to signalIdentity,
            "announcedRevision" to revision,
            "publishedAt" to scope.publishedAt,
        ),
    )

    private fun billRequest(
        scope: Scope,
        revision: String,
        fingerprint: String,
        unavailableReadCount: Int? = null,
        records: List<Map<String, Any>> = emptyList(),
    ): Map<String, Any?> = mapOf(
        "channelId" to scope.channelId,
        "billIdentity" to scope.billIdentity,
        "businessDate" to scope.businessDate,
        "currency" to scope.currency,
        "businessTimezone" to scope.timezone,
        "revision" to revision,
        "completeness" to "COMPLETE",
        "rawEvidence" to "evidence://${scope.billIdentity}/$revision",
        "payloadFingerprint" to fingerprint,
        "publishedAt" to scope.publishedAt,
        "records" to records,
        "unavailableReadCount" to unavailableReadCount,
    )

    private fun channelOnlyRecord(scope: Scope): Map<String, Any> = mapOf(
        "recordIdentity" to "${scope.billIdentity}-record-1",
        "channelTransactionIdentity" to "${scope.billIdentity}-tx-1",
        "transactionKind" to "PAYMENT",
        "money" to mapOf("currency" to scope.currency, "amountMinor" to "1000"),
        "rawStatus" to "SUCCESS",
        "occurredAt" to scope.publishedAt,
        "receivedAt" to scope.publishedAt.plusSeconds(1),
        "rawEvidence" to "evidence://${scope.billIdentity}/record-1",
    )

    private fun actorHeader(): Map<String, String> = mapOf(
        "X-Reference-Actor-Context" to "fixture-reconciliation-operator",
    )

    private fun unknownActorHeader(): Map<String, String> = mapOf(
        "X-Reference-Actor-Context" to "unknown-reference-actor",
    )

    private fun postJson(
        path: String,
        payload: Any,
        expectedStatus: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ): JsonNode {
        val builder = post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload))
        headers.forEach { (name, value) -> builder.header(name, value) }
        val result = mockMvc.perform(builder).andExpect(status().`is`(expectedStatus)).andReturn()
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun postJsonResult(
        path: String,
        payload: Any,
        headers: Map<String, String> = emptyMap(),
    ): HttpJsonResult {
        val builder = post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload))
        headers.forEach { (name, value) -> builder.header(name, value) }
        val result = mockMvc.perform(builder).andReturn()
        val body = result.response.contentAsByteArray.takeIf { it.isNotEmpty() }
            ?.let(objectMapper::readTree)
            ?: objectMapper.createObjectNode()
        return HttpJsonResult(result.response.status, body)
    }

    private fun getJson(path: String): JsonNode = objectMapper.readTree(
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsByteArray,
    )

    private fun count(sql: String, vararg args: Any): Long = requireNotNull(
        jdbcTemplate.queryForObject(sql, Long::class.java, *args),
    )

    private fun runCount(scope: Scope): Long = count(
        "select count(*) from reconciliation_run r join reconciliation_batch b on b.id = r.batch_id " +
            "where b.channel_id = ? and b.currency = ? and b.reconciliation_date = ?",
        scope.channelId,
        scope.currency,
        scope.businessDate,
    )

    private fun readAttempts(scope: Scope): Long = count(
        "select read_attempt_count from authoritative_bill where channel_id = ? and bill_identity = ?",
        scope.channelId,
        scope.billIdentity,
    )

    private fun signalReadAttempts(scope: Scope, signalIdentity: String): Long = count(
        "select s.fetch_attempt_count from bill_available_signal s join authoritative_bill b on b.id = s.authoritative_bill_id " +
            "where b.channel_id = ? and b.bill_identity = ? and s.signal_identity = ?",
        scope.channelId,
        scope.billIdentity,
        signalIdentity,
    )

    private fun assertInvalidCursor(result: HttpJsonResult) {
        assertThat(result.status).isEqualTo(400)
        assertThat(result.body.requiredText("code")).isEqualTo("INVALID_CURSOR")
    }

    private fun assertValidationError(result: HttpJsonResult) {
        assertThat(result.status).isEqualTo(400)
        assertThat(result.body.requiredText("code")).isEqualTo("VALIDATION_ERROR")
    }

    private fun assertDescending(items: JsonNode) {
        val entries = items.map { Instant.parse(it.requiredText("sortTime")) to it.requiredText("runId") }
        entries.zipWithNext().forEach { (left, right) ->
            assertThat(left.first.isAfter(right.first) || (left.first == right.first && left.second > right.second)).isTrue()
        }
    }

    private data class HttpJsonResult(val status: Int, val body: JsonNode)

    private data class Scope(
        val sequence: Int,
        val channelId: String,
        val billIdentity: String,
        val businessDate: LocalDate,
        val currency: String = "CNY",
        val timezone: String = "Asia/Shanghai",
        val publishedAt: Instant,
    )

    private companion object {
        private val sequence = AtomicInteger()

        fun nextScope(label: String): Scope {
            val next = sequence.incrementAndGet()
            return Scope(
                sequence = next,
                channelId = "C-BILL-$label-$next",
                billIdentity = "bill-$label-$next",
                businessDate = LocalDate.of(2031, 1, 1).plusDays(next.toLong()),
                publishedAt = Instant.parse("2031-01-01T00:00:00Z").plusSeconds(next.toLong()),
            )
        }
    }

    private fun JsonNode.requiredText(field: String): String =
        requireNotNull(get(field)) { "missing JSON field $field in $this" }.asText()
}
