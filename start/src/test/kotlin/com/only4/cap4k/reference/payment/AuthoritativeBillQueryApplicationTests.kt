package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Focused persistence and adapter checks; the change-level acceptance uses an external HTTP client. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthoritativeBillQueryApplicationTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val objectMapper: ObjectMapper,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
) {
    @Test
    fun `both bill routes expose complete immutable history independent of reconciliation`() {
        val identity = "bill-query-${UUID.randomUUID()}"
        val first = revision(identity, "1", "INCOMPLETE", "2031-01-01T01:00:00Z", "fp-1",
            listOf(record(identity, "payment", "PAYMENT", "1000")))
        val second = revision(identity, "2", "COMPLETE", "2031-01-02T02:00:00Z", "fp-2",
            listOf(record(identity, "refund", "REFUND", "300"), record(identity, "payment-2", "PAYMENT", "700")))
        val registered = postJson("/api/reference-fixtures/bills", second)
        val billId = registered.requiredText("billId")
        postJson("/api/reference-fixtures/bills", first)

        val detail = getJson("/api/authoritative-bills/$billId")
        val history = getJson("/api/authoritative-bills/$billId/revisions")
        assertThat(history).isEqualTo(detail)
        assertThat(detail.requiredText("billId")).isEqualTo(billId)
        assertThat(detail.requiredText("billIdentity")).isEqualTo(identity)
        assertThat(detail.requiredText("channelId")).isEqualTo("C-QUERY")
        assertThat(detail.requiredText("currency")).isEqualTo("CNY")
        assertThat(detail.requiredText("businessDate")).isEqualTo("2031-01-01")
        assertThat(detail.requiredText("businessTimezone")).isEqualTo("Asia/Shanghai")
        assertThat(detail.requiredText("createdAt")).isNotBlank()
        assertThat(detail.requiredText("currentRevision")).isEqualTo("2")
        assertThat(detail.requiredText("currentRevisionId")).isEqualTo(detail["revisions"][1].requiredText("revisionId"))
        assertThat(detail["revisions"].map { it.requiredText("revision") }).containsExactly("1", "2")
        assertThat(detail["revisions"][0].requiredText("publishedAt")).isEqualTo("2031-01-01T01:00:00Z")
        assertThat(detail["revisions"][0].requiredText("completeness")).isEqualTo("INCOMPLETE")
        assertThat(detail["revisions"][0].requiredText("payloadFingerprint")).isEqualTo("fp-1")
        assertThat(detail["revisions"][0].requiredText("rawEvidence")).isEqualTo("evidence://$identity/1")
        val records = detail["revisions"][1]["records"]
        assertThat(records.map { it.requiredText("recordIdentity") }).containsExactly("$identity-payment-2", "$identity-refund")
        assertThat(records[0].requiredText("externalTransactionIdentity")).isEqualTo("$identity-payment-2-tx")
        assertThat(records[0].requiredText("transactionKind")).isEqualTo("PAYMENT")
        assertThat(records[0].at("/money/currency").asText()).isEqualTo("CNY")
        assertThat(records[0].at("/money/amountMinor").asText()).isEqualTo("700")
        assertThat(records[0].requiredText("rawStatus")).isEqualTo("SUCCESS")
        assertThat(records[0].requiredText("occurredAt")).isEqualTo("2031-01-01T00:00:00Z")
        assertThat(records[0].requiredText("receivedAt")).isEqualTo("2031-01-01T00:00:01Z")
        assertThat(records[0].requiredText("rawEvidence")).isEqualTo("evidence://$identity/payment-2")
        assertThat(requireNotNull(jdbcTemplate.queryForObject("select count(*) from reconciliation_run", Long::class.java))).isZero()
    }

    @Test
    fun `same revision checks all immutable content and unknown bill has stable error`() {
        val identity = "bill-query-${UUID.randomUUID()}"
        val original = revision(identity, "1", "COMPLETE", "2031-01-01T01:00:00Z", "fp-1",
            listOf(record(identity, "payment", "PAYMENT", "1000")))
        val billId = postJson("/api/reference-fixtures/bills", original).requiredText("billId")
        val replay = postJson("/api/reference-fixtures/bills", original)
        assertThat(replay["idempotentReplay"].asBoolean()).isTrue()

        val changed = original + ("records" to listOf(record(identity, "payment", "PAYMENT", "2000")))
        val conflict = postResult("/api/reference-fixtures/bills", changed)
        assertThat(conflict.first).isEqualTo(400)
        assertThat(conflict.second.requiredText("code")).isEqualTo("VALIDATION_ERROR")
        val detail = getJson("/api/authoritative-bills/$billId")
        assertThat(detail["revisions"]).hasSize(1)
        assertThat(detail["revisions"][0]["records"][0].at("/money/amountMinor").asText()).isEqualTo("1000")

        val missing = getResult("/api/authoritative-bills/01900000-0000-7000-8000-000000000001/revisions")
        assertThat(missing.first).isEqualTo(404)
        assertThat(missing.second.requiredText("code")).isEqualTo("AUTHORITATIVE_BILL_NOT_FOUND")
        assertThat(missing.second["details"].requiredText("billId"))
            .isEqualTo("01900000-0000-7000-8000-000000000001")
        assertThat(missing.second.requiredText("correlationId")).isNotBlank()
        assertThat(missing.second["retryable"].asBoolean()).isFalse()
    }

    private fun revision(
        identity: String,
        revision: String,
        completeness: String,
        publishedAt: String,
        fingerprint: String,
        records: List<Map<String, Any>>,
    ): Map<String, Any> = mapOf(
        "channelId" to "C-QUERY",
        "billIdentity" to identity,
        "businessDate" to LocalDate.parse("2031-01-01"),
        "currency" to "CNY",
        "businessTimezone" to "Asia/Shanghai",
        "revision" to revision,
        "completeness" to completeness,
        "publishedAt" to Instant.parse(publishedAt),
        "payloadFingerprint" to fingerprint,
        "rawEvidence" to "evidence://$identity/$revision",
        "records" to records,
    )

    private fun record(identity: String, label: String, kind: String, amountMinor: String): Map<String, Any> = mapOf(
        "recordIdentity" to "$identity-$label",
        "channelTransactionIdentity" to "$identity-$label-tx",
        "transactionKind" to kind,
        "money" to mapOf("currency" to "CNY", "amountMinor" to amountMinor),
        "rawStatus" to "SUCCESS",
        "occurredAt" to Instant.parse("2031-01-01T00:00:00Z"),
        "receivedAt" to Instant.parse("2031-01-01T00:00:01Z"),
        "rawEvidence" to "evidence://$identity/$label",
    )

    private fun postJson(path: String, payload: Any): JsonNode {
        val result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(payload))).andExpect(status().isOk).andReturn()
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun postResult(path: String, payload: Any): Pair<Int, JsonNode> {
        val result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(payload))).andReturn()
        return result.response.status to objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun getJson(path: String): JsonNode {
        val result = mockMvc.perform(get(path)).andExpect(status().isOk).andReturn()
        return objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun getResult(path: String): Pair<Int, JsonNode> {
        val result = mockMvc.perform(get(path)).andReturn()
        return result.response.status to objectMapper.readTree(result.response.contentAsByteArray)
    }

    private fun JsonNode.requiredText(field: String): String =
        requireNotNull(get(field)) { "missing $field in $this" }.asText()
}
