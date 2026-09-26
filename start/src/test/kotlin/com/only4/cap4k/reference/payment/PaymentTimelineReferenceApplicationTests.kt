package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.adapter.start.PaymentExpiryScheduler
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.contract.common.ManualReviewBlockingScope
import com.only4.cap4k.reference.payment.contract.common.ManualReviewEvidenceRef
import com.only4.cap4k.reference.payment.contract.common.ManualReviewReference
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
class PaymentTimelineReferenceApplicationTests(
    @param:Autowired private val mvc: MockMvc,
    @param:Autowired private val json: ObjectMapper,
    @param:Autowired private val jdbc: JdbcTemplate,
    @param:Autowired private val paymentExpiryScheduler: PaymentExpiryScheduler,
) {
    @Test
    fun `unknown payment attempt is not exposed as ordinary terminal result`() {
        postJson("/api/reference-fixtures/clock/set", mapOf("instant" to Instant.parse("2026-09-23T00:00:00Z")))
        val paymentId = createPayment()
        val attemptId = postJson(
            "/api/payments/$paymentId/attempts",
            mapOf("idempotencyKey" to "timeline-unknown-${UUID.randomUUID()}"),
        )["paymentAttemptId"].asText()
        jdbc.update(
            "update payment set expires_at = ? where id = ?",
            LocalDateTime.parse("2026-09-22T00:00:00"), paymentId,
        )
        paymentExpiryScheduler.expirePayments()
        val events = getJson("/api/payments/$paymentId/timeline")["items"].toList()
        assertThat(events.map { it["eventType"].asText() }).contains("PAYMENT_ATTEMPT_RESULT_UNKNOWN")
        assertThat(events.map { it["eventType"].asText() }).doesNotContain("PAYMENT_ATTEMPT_TERMINAL")
        assertThat(events.single { it["eventType"].asText() == "PAYMENT_ATTEMPT_RESULT_UNKNOWN" }["refs"]["paymentAttemptId"].asText())
            .isEqualTo(attemptId)
    }

    @Test
    fun `timeline traces payment refund bill reconciliation settlement notification and manual review`() {
        val marker = UUID.randomUUID().toString()
        val date = LocalDate.parse("2026-09-20")
        val paymentOccurredAt = Instant.parse("2026-09-20T02:00:00Z")
        val refundOccurredAt = Instant.parse("2026-09-20T04:00:00Z")
        val paymentTransaction = "CT-timeline-$marker"
        val billIdentity = "bill-timeline-$marker"
        val paymentId = run {
            postJson("/api/reference-fixtures/clock/set", mapOf("instant" to paymentOccurredAt.minusSeconds(60)))
            createPayment()
        }
        val paymentAttemptId = postJson(
            "/api/payments/$paymentId/attempts",
            mapOf("idempotencyKey" to "timeline-attempt-$marker"),
        )["paymentAttemptId"].asText()
        postJson(
            "/api/payments/$paymentId/attempts/$paymentAttemptId/submissions",
            mapOf("idempotencyKey" to "timeline-submit-$marker"),
        )
        registerCallbackEvidence("PAYMENT", "payment-$marker", "$paymentId|$paymentAttemptId|$paymentTransaction", "100.00")
        postJson(
            "/api/channel/payment-results",
            mapOf(
                "channelId" to "C-001", "notificationId" to "payment-$marker",
                "paymentId" to paymentId, "paymentAttemptId" to paymentAttemptId,
                "channelTransactionId" to paymentTransaction, "money" to money("100.00"),
                "result" to "SUCCESS", "occurredAt" to paymentOccurredAt,
                "rawPayload" to "reference-timeline-payment",
            ),
        )

        postJson("/api/reference-fixtures/clock/set", mapOf("instant" to refundOccurredAt.minusSeconds(60)))
        val refundId = postJson(
            "/api/refunds",
            mapOf(
                "merchantId" to "M-001", "merchantRefundNo" to "R-timeline-$marker",
                "idempotencyKey" to "timeline-refund-$marker", "paymentId" to paymentId,
                "money" to money("20.00"), "reason" to "timeline trace",
            ),
        )["refundId"].asText()
        val refundAttemptId = postJson(
            "/api/refunds/$refundId/attempts",
            mapOf("idempotencyKey" to "timeline-refund-attempt-$marker"),
        )["refundAttemptId"].asText()
        postJson(
            "/api/refunds/$refundId/attempts/$refundAttemptId/submissions",
            mapOf("idempotencyKey" to "timeline-refund-submit-$marker"),
        )
        val refundTransaction = getJson("/api/refunds/$refundId")["attempts"][0]["channelRefundId"].asText()
        registerCallbackEvidence("REFUND", "refund-$marker", "$refundId|$refundAttemptId|$refundTransaction", "20.00")
        postJson(
            "/api/channel/refund-results",
            mapOf(
                "channelId" to "C-001", "notificationId" to "refund-$marker",
                "refundId" to refundId, "refundAttemptId" to refundAttemptId,
                "channelRefundId" to refundTransaction, "money" to money("20.00"),
                "result" to "SUCCESS", "occurredAt" to refundOccurredAt,
                "rawPayload" to "reference-timeline-refund",
            ),
        )

        val records = listOf(
            billRecord("$billIdentity-payment", "PAYMENT", paymentTransaction, "100.00", paymentOccurredAt),
            billRecord("$billIdentity-refund", "REFUND", refundTransaction, "20.00", refundOccurredAt),
        )
        val billId = (1..2).map { revision ->
            val registered = postJson(
                "/api/reference-fixtures/bills",
                mapOf(
                    "channelId" to "C-001", "billIdentity" to billIdentity,
                    "businessDate" to date, "currency" to "CNY", "businessTimezone" to "Asia/Shanghai",
                    "revision" to revision.toString(), "completeness" to "COMPLETE",
                    "rawEvidence" to "evidence://$billIdentity/$revision",
                    "payloadFingerprint" to "$billIdentity-fingerprint-$revision",
                    "publishedAt" to Instant.parse("2026-09-21T00:00:00Z").plusSeconds(revision.toLong()),
                    "records" to records,
                ),
            )
            val signal = postJson(
                "/api/reference-fixtures/bills/$billIdentity/signals",
                mapOf(
                    "channelId" to "C-001", "businessDate" to date, "currency" to "CNY",
                    "businessTimezone" to "Asia/Shanghai", "signalIdentity" to "$billIdentity-signal-$revision",
                    "announcedRevision" to revision.toString(),
                    "publishedAt" to Instant.parse("2026-09-21T00:00:00Z").plusSeconds(revision.toLong()),
                ),
            )
            assertThat(signal["runStatus"].asText()).isEqualTo("COMPLETED")
            registered["billId"].asText()
        }
        assertThat(billId.distinct()).hasSize(1)

        val period = mapOf(
            "start" to date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
            "end" to date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
            "timezone" to "Asia/Shanghai",
        )
        val settlementId = postJson(
            "/api/merchant-settlements",
            mapOf(
                "idempotencyKey" to "timeline-settlement-prepare-$marker",
                "merchantId" to "M-001",
                "currency" to "CNY",
                "settlementPeriod" to period,
            ),
            actor = true,
        )["settlementId"].asText()
        postJson(
            "/api/merchant-settlements/$settlementId/confirmations",
            mapOf(
                "idempotencyKey" to "timeline-settlement-confirm-$marker",
                "settlementId" to settlementId, "executionChannelId" to "C-001",
                "reason" to "timeline settlement composition reviewed",
                "evidence" to "evidence://timeline/settlement/$marker",
            ), actor = true,
        )
        val executionId = "timeline-settlement-execution-$marker"
        val execution = postJson(
            "/api/merchant-settlements/$settlementId/executions",
            mapOf(
                "idempotencyKey" to "timeline-settlement-execute-$marker",
                "merchantId" to "M-001", "settlementId" to settlementId,
                "executionId" to executionId, "executionChannelId" to "C-001",
            ), actor = true,
        )
        assertThat(execution["executionId"].asText()).isEqualTo(executionId)
        val settlementAttemptId = execution["attemptId"].asText()
        val groupIdentity = execution["executionGroupIdentity"].asText()
        val requestIdentity = execution["requestIdentity"].asText()
        val externalIdentity = "STL-$requestIdentity"
        val settlementMoney = getJson("/api/merchant-settlements/$settlementId")["netMoney"]
        val settlementAmount = BigDecimal(settlementMoney["amountMinor"].asText()).movePointLeft(2)
        registerCallbackEvidence(
            "SETTLEMENT", "settlement-$marker",
            "$settlementId|$executionId|$groupIdentity|$requestIdentity|$externalIdentity",
            settlementAmount.toPlainString(),
        )
        postJson(
            "/api/channel/settlement-results",
            mapOf(
                "channelId" to "C-001", "notificationId" to "settlement-$marker",
                "settlementId" to settlementId, "executionId" to executionId,
                "executionAttemptId" to settlementAttemptId,
                "executionGroupIdentity" to groupIdentity, "requestIdentity" to requestIdentity,
                "externalSettlementIdentity" to externalIdentity, "money" to money(settlementAmount.toPlainString()),
                "result" to "SUCCESS", "resultCode" to "SUCCESS",
                "occurredAt" to Instant.parse("2026-09-22T03:05:00Z"),
                "receivedAt" to Instant.parse("2026-09-22T03:05:30Z"),
                "rawPayload" to "reference-timeline-settlement",
            ), actor = true,
        )
        val settlementAttempt = getJson("/api/merchant-settlements/$settlementId")["attempts"]
            .single { it["attemptId"].asText() == settlementAttemptId }
        assertThat(settlementAttempt["executionId"].asText()).isEqualTo(executionId)
        assertThat(settlementAttempt["receipts"].single()["executionId"].asText()).isEqualTo(executionId)

        Mediator.commands.send(ManualReviewSupportTestCommand.Request(
            ManualReviewSupport.Opening(
                reviewIdentity = "timeline-manual-$marker", type = "PAYMENT_RESULT_REVIEW",
                merchantId = "M-001", originKind = "PAYMENT_REVIEW", originIdentity = paymentId,
                summary = "payment timeline review",
                relatedRefs = listOf(ManualReviewReference("PAYMENT", paymentId)),
                blockingScopes = listOf(ManualReviewBlockingScope("PAYMENT", paymentId)),
                evidenceRefs = listOf(ManualReviewEvidenceRef("PAYMENT", paymentId, "trace")),
            ),
        ))

        val all = getJson("/api/payments/$paymentId/timeline?pageSize=100")["items"].toList()
        val types = all.map { it["eventType"].asText() }
        assertThat(types).contains(
            "PAYMENT_SUCCEEDED", "PAYMENT_RESULT_RECEIPT", "REFUND_REQUESTED",
            "REFUND_RESULT_RECEIPT", "RECONCILIATION_BATCH", "RECONCILIATION_RUN",
            "RECONCILIATION_ITEM", "AUTHORITATIVE_BILL", "BILL_REVISION",
            "BILL_AVAILABLE", "BILL_REVISION_RECORD", "SETTLEMENT_SUCCEEDED",
            "SETTLEMENT_RESULT_RECEIPT", "SETTLEMENT_EXECUTION",
            "MERCHANT_NOTIFICATION", "MERCHANT_NOTIFICATION_DELIVERY", "MANUAL_REVIEW",
        )
        assertThat(types.count { it == "AUTHORITATIVE_BILL" }).isEqualTo(1)
        assertThat(types.count { it == "BILL_REVISION" }).isEqualTo(2)
        assertThat(types.count { it == "BILL_AVAILABLE" }).isEqualTo(2)
        assertThat(types.count { it == "BILL_REVISION_RECORD" }).isEqualTo(4)
        assertThat(all.map { it["eventId"].asText() }).doesNotHaveDuplicates()
        assertThat(all.first { it["eventType"].asText() == "RECONCILIATION_BATCH" }["outcome"].asText())
            .isEqualTo("COMPLETED")
        assertThat(all.first { it["eventType"].asText() == "RECONCILIATION_ITEM" }["outcome"].asText())
            .isEqualTo("MATCHED")
        assertThat(all.first { it["eventType"].asText() == "BILL_REVISION" }["outcome"].asText())
            .isEqualTo("COMPLETE")
        assertThat(all.first { it["eventType"].asText() == "MERCHANT_NOTIFICATION" }["outcome"].asText())
            .isIn("PENDING", "DELIVERED", "FAILED", "RESULT_UNKNOWN")
        assertThat(all.first { it["eventType"].asText() == "MERCHANT_NOTIFICATION_DELIVERY" }["outcome"].asText())
            .isIn("SUCCESS", "FAILURE", "RESULT_UNKNOWN")
        assertThat(all.first { it["eventType"].asText() == "SETTLEMENT_EXECUTION" }["outcome"].asText())
            .isEqualTo("SUCCEEDED")
        assertThat(all.first { it["eventType"].asText() == "SETTLEMENT_EXECUTION" }["refs"]["executionId"].asText())
            .isEqualTo(executionId)
        assertThat(all.first { it["eventType"].asText() == "MANUAL_REVIEW" }["outcome"].asText())
            .isEqualTo("OPEN")
        assertThat(all.first { it["eventType"].asText() == "PAYMENT_ATTEMPT_TERMINAL" }["outcome"].asText())
            .isEqualTo("SUCCESS")
        assertThat(all.first { it["eventType"].asText() == "REFUND_ATTEMPT_RESULT" }["outcome"].asText())
            .isEqualTo("SUCCESS")
        assertThat(all.first { it["eventType"].asText() == "PAYMENT_RESULT_RECEIPT" }["occurredAt"].asText())
            .isEqualTo(paymentOccurredAt.toString())
        assertThat(all.first { it["eventType"].asText() == "PAYMENT_ATTEMPT_TERMINAL" }["occurredAt"].asText())
            .isEqualTo(paymentOccurredAt.toString())
        assertThat(all.first { it["eventType"].asText() == "REFUND_RESULT_RECEIPT" }["occurredAt"].asText())
            .isEqualTo(refundOccurredAt.toString())
        assertThat(all.first { it["eventType"].asText() == "REFUND_ATTEMPT_RESULT" }["occurredAt"].asText())
            .isEqualTo(refundOccurredAt.toString())
        assertThat(all.first { it["eventType"].asText() == "SETTLEMENT_RESULT_RECEIPT" }["occurredAt"].asText())
            .isEqualTo("2026-09-22T03:05:00Z")

        val sorted = all.map { Instant.parse(it["recordedAt"].asText()) to it["eventId"].asText() }
        assertThat(sorted).isEqualTo(sorted.sortedWith(compareBy<Pair<Instant, String>>({ it.first }, { it.second })))
        val paged = mutableListOf<JsonNode>()
        var cursor: String? = null
        do {
            val page = getJson("/api/payments/$paymentId/timeline?pageSize=7" +
                (cursor?.let { "&cursor=$it" } ?: ""))
            paged += page["items"].toList()
            cursor = page["nextCursor"].takeUnless { it.isNull }?.asText()
        } while (cursor != null)
        assertThat(paged.map { it["eventId"].asText() })
            .containsExactlyElementsOf(all.map { it["eventId"].asText() })
    }

    @Test
    fun `SQL timeline pages creation operation and attempt without missing equal-time events`() {
        val paymentId = createPayment()
        val attempt = postJson(
            "/api/payments/$paymentId/attempts",
            mapOf("idempotencyKey" to "timeline-attempt-${UUID.randomUUID()}"),
        )
        assertThat(attempt["paymentAttemptId"].asText()).isNotBlank()

        val all = getJson("/api/payments/$paymentId/timeline?")["items"].toList()
        assertThat(all.map { it["eventType"].asText() }).contains(
            "PAYMENT_CREATED", "PAYMENT_ATTEMPT_CREATED", "OPERATION_ACCEPTED",
        )
        assertThat(all).allSatisfy { entry ->
            assertThat(entry["refs"]["paymentId"].asText()).isEqualTo(paymentId)
            assertThat(entry["recordedAt"].asText()).isNotBlank()
        }
        val sorted = all.map { it["recordedAt"].asText() to it["eventId"].asText() }
        assertThat(sorted).isEqualTo(sorted.sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second })))

        val paged = mutableListOf<JsonNode>()
        var cursor: String? = null
        do {
            val suffix = cursor?.let { "&cursor=$it" } ?: ""
            val page = getJson("/api/payments/$paymentId/timeline?pageSize=1$suffix")
            paged += page["items"].toList()
            cursor = page["nextCursor"].takeUnless { it.isNull }?.asText()
        } while (cursor != null)
        assertThat(paged.map { it["eventId"].asText() }).containsExactlyElementsOf(all.map { it["eventId"].asText() })
        assertThat(paged.map { it["eventId"].asText() }.toSet()).hasSize(paged.size)

        val otherPaymentId = createPayment()
        val page = getJson("/api/payments/$paymentId/timeline?pageSize=1")
        val foreignCursor = page["nextCursor"].asText()
        val rejected = mvc.perform(get("/api/payments/$otherPaymentId/timeline").param("cursor", foreignCursor)).andReturn()
        assertThat(rejected.response.status).isEqualTo(400)
        assertThat(json.readTree(rejected.response.contentAsByteArray)["code"].asText()).isEqualTo("INVALID_CURSOR")
    }

    private fun createPayment(): String = postJson(
        "/api/payments",
        mapOf(
            "merchantId" to "M-001",
            "merchantOrderNumber" to "timeline-${UUID.randomUUID()}",
            "idempotencyKey" to "timeline-${UUID.randomUUID()}",
            "money" to mapOf("currency" to "CNY", "amountMinor" to "10000"),
            "paymentMethod" to "CARD",
            "expiresAt" to Instant.parse("2030-01-01T00:00:00Z"),
        ),
    )["paymentId"].asText()

    private fun money(amount: String): Map<String, String> = mapOf(
        "currency" to "CNY",
        "amountMinor" to BigDecimal(amount).movePointRight(2).toBigIntegerExact().toString(),
    )

    private fun billRecord(
        identity: String, kind: String, transaction: String, amount: String, occurredAt: Instant,
    ): Map<String, Any> = mapOf(
        "recordIdentity" to identity, "channelTransactionIdentity" to transaction,
        "transactionKind" to kind, "money" to money(amount),
        "rawStatus" to "SUCCEEDED", "occurredAt" to occurredAt,
        "receivedAt" to occurredAt.plusSeconds(30),
        "rawEvidence" to "evidence://$identity",
    )

    private fun registerCallbackEvidence(
        kind: String, notificationId: String, associationIdentity: String, amount: String,
    ) {
        val payload = when (kind) {
            "PAYMENT" -> "reference-timeline-payment"
            "REFUND" -> "reference-timeline-refund"
            else -> "reference-timeline-settlement"
        }
        postJson(
            "/api/reference-fixtures/callback-evidence",
            mapOf(
                "idempotencyKey" to "evidence-$notificationId", "kind" to kind,
                "channelId" to "C-001", "externalIdentity" to notificationId,
                "associationIdentity" to associationIdentity, "money" to money(amount),
                "rawPayload" to payload,
            ),
        )
    }

    private fun postJson(path: String, body: Any, actor: Boolean = false): JsonNode {
        val request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))
        if (actor) request.header("X-Reference-Actor-Context", "fixture-settlement-operator")
        val result = mvc.perform(request).andReturn()
        assertThat(result.response.status).withFailMessage(result.response.contentAsString).isBetween(200, 299)
        return json.readTree(result.response.contentAsByteArray)
    }

    private fun getJson(path: String): JsonNode {
        val result = mvc.perform(get(path)).andReturn()
        assertThat(result.response.status).isEqualTo(200)
        return json.readTree(result.response.contentAsByteArray)
    }
}
