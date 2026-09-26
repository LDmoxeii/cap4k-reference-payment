package com.only4.cap4k.reference.payment.adapter.application.queries.payment.read

import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.adapter.application.queries.ReferenceCursorToken
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentTimelineEndpoint
import com.only4.cap4k.reference.payment.domain.values.Money as DomainMoney
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/**
 * A single bounded SQL page over persisted facts. Each branch has an explicit relational payment
 * path; JSON reference snapshots and external transaction strings are never used as fuzzy joins.
 * The event key uses the persisted row identity and stage, so equal timestamps remain orderable.
 */
@Service
class PaymentTimelineReadModel(private val entityManager: EntityManager) {
    fun get(request: GetPaymentTimelineEndpoint.Request): GetPaymentTimelineEndpoint.Response {
        val paymentId = request.paymentId.trim().also { require(it.isNotEmpty()) { "paymentId 不能为空" } }
        val pageSize = (request.pageSize ?: 50).also { require(it in 1..100) { "pageSize 必须在 1 到 100 之间" } }
        val fingerprint = sha256("payment-timeline:v1:$paymentId")
        val cursor = request.cursor?.trim()?.takeIf(String::isNotEmpty)?.let { Cursor.decode(it, fingerprint) }
        val exists = entityManager.createNativeQuery("select count(*) from payment where id = ?1")
            .setParameter(1, paymentId).singleResult as Number
        require(exists.toLong() == 1L) { "未找到支付：$paymentId" }

        val sql = buildString {
            append("select event_id,event_type,recorded_at,occurred_at,resource_type,resource_id,")
            append("identity,outcome,actor_id,reason,evidence,amount,currency from (")
            append(branches.joinToString(" union all "))
            append(") timeline ")
            if (cursor != null) append("where (recorded_at > :cursorTime or (recorded_at = :cursorTime and event_id > :cursorId)) ")
            append("order by recorded_at asc,event_id asc limit :pageLimit")
        }
        val query = entityManager.createNativeQuery(sql)
            .setParameter("paymentId", paymentId)
            .setParameter("pageLimit", pageSize + 1)
        if (cursor != null) {
            query.setParameter("cursorTime", LocalDateTime.ofInstant(cursor.recordedAt, ZoneOffset.UTC))
            query.setParameter("cursorId", cursor.eventId)
        }
        val rows = query.resultList.map { it as Array<Any?> }
        val items = rows.take(pageSize).map { row ->
            val eventType = row[1].toString()
            val resourceType = row[4].toString()
            val resourceId = row[5].toString()
            val identity = row[6]?.toString()
            val outcome = row[7]?.toString()
            val actor = row[8]?.toString()
            val reason = row[9]?.toString()
            val evidence = row[10]?.toString()
            val currency = row[12]?.toString()
            val money = if (row[11] != null && currency != null) {
                val amount = when (val value = row[11]) {
                    is BigDecimal -> value
                    else -> BigDecimal(value.toString())
                }
                Money(currency, amount.movePointRight(DomainMoney.fractionDigits(currency)).toBigIntegerExact().toString())
            } else null
            val refs = mapOf("paymentId" to paymentId, resourceType to resourceId)
            GetPaymentTimelineEndpoint.Response.Entry(
                eventId = row[0].toString(),
                eventType = eventType,
                category = eventType.substringBefore('_'),
                recordedAt = asInstant(requireNotNull(row[2])),
                occurredAt = row[3]?.let(::asInstant),
                payload = buildMap {
                    identity?.let { put("identity", it) }
                    outcome?.let { put("outcome", it) }
                    actor?.let { put("actorId", it) }
                    reason?.let { put("reason", it) }
                    evidence?.let { put("evidence", it) }
                    money?.let { put("money", it) }
                },
                refs = refs,
                relatedResourceRefs = refs,
                outcome = outcome,
                actorId = actor,
                reason = reason,
                evidenceRefs = evidence?.let(::listOf) ?: emptyList(),
                money = money,
            )
        }
        return GetPaymentTimelineEndpoint.Response(
            items = items,
            nextCursor = if (rows.size > pageSize) items.last().let { Cursor.encode(fingerprint, it.recordedAt, it.eventId) } else null,
            pageSize = pageSize,
        )
    }

    private data class Cursor(val fingerprint: String, val recordedAt: Instant, val eventId: String) {
        companion object {
            fun encode(fingerprint: String, recordedAt: Instant, eventId: String): String =
                ReferenceCursorToken.encode("v1", fingerprint, recordedAt.toString(), eventId)

            fun decode(raw: String, expectedFingerprint: String): Cursor = try {
                val values = ReferenceCursorToken.decode(raw, 4)
                if (values.size != 4 || values[0] != "v1" || values[1] != expectedFingerprint || values[3].isBlank()) {
                    throw InvalidCursorException()
                }
                Cursor(values[1], Instant.parse(values[2]), values[3])
            } catch (error: InvalidCursorException) {
                throw error
            } catch (_: Exception) {
                throw InvalidCursorException()
            }
        }
    }

    private companion object {
        // Every SELECT has exactly the same projection, allowing the database to apply LIMIT
        // after UNION ALL and the ASC keyset predicate instead of materialising aggregates.
        fun branch(
            type: String, from: String, id: String, recorded: String,
            occurred: String = "null", resource: String, resourceId: String = id,
            identity: String = "null", outcome: String = "null", actor: String = "null",
            reason: String = "null", evidence: String = "null",
            amount: String = "null", currency: String = "null",
        ): String = """
            select '$type:' || cast($id as varchar(256)) as event_id,
                   '$type' as event_type,
                   $recorded as recorded_at,
                   $occurred as occurred_at,
                   '$resource' as resource_type,
                   cast($resourceId as varchar(256)) as resource_id,
                   cast($identity as varchar(512)) as identity,
                   cast($outcome as varchar(128)) as outcome,
                   cast($actor as varchar(128)) as actor_id,
                   cast($reason as varchar(2048)) as reason,
                   cast($evidence as varchar(4096)) as evidence,
                   cast($amount as decimal(19,4)) as amount,
                   cast($currency as varchar(3)) as currency
              from $from
        """.trimIndent()

        // Native SQL bypasses JPA enum converters. Keep the public timeline vocabulary stable
        // instead of leaking the integer values stored by the CAP4K aggregate mappings.
        fun enumName(column: String, vararg names: String): String =
            "case $column " + names.mapIndexed { index, name -> "when $index then '$name'" }
                .joinToString(" ") + " else 'UNRECOGNIZED' end"

        private const val LINKED_ITEM =
            "(ri.payment_id = :paymentId or ri.refund_id in (select rf.id from refund rf where rf.payment_id = :paymentId))"
        private const val LINKED_LINE =
            "(sl.payment_id = :paymentId or sl.refund_id in (select rf.id from refund rf where rf.payment_id = :paymentId))"
        private const val LINKED_RUN =
            "exists (select 1 from reconciliation_item ri where ri.reconciliation_run_id = rr.id and $LINKED_ITEM)"
        private const val LINKED_SETTLEMENT =
            "exists (select 1 from settlement_line sl where sl.merchant_settlement_id = s.id and $LINKED_LINE)"

        val branches = listOf(
            branch("PAYMENT_CREATED", "payment p where p.id = :paymentId", "p.id", "p.created_at",
                resource = "paymentId", identity = "p.idempotency_key", outcome = "'PAYABLE'", amount = "p.amount", currency = "p.currency"),
            branch("PAYMENT_SUCCEEDED", "payment p where p.id = :paymentId and p.success_fact_formed = true", "p.id",
                "coalesce((select min(n.first_received_at) from payment_notification_receipt n join payment_attempt a on a.id = n.payment_attempt_id where a.payment_id = p.id and n.accepted = true),p.succeeded_at)",
                "p.succeeded_at", "paymentId", identity = "p.merchant_order_success_identity", outcome = "'SUCCEEDED'",
                amount = "p.amount", currency = "p.currency"),
            branch("PAYMENT_CLOSED", "payment p where p.id = :paymentId and p.closed_at is not null", "p.id",
                "p.closed_at", resource = "paymentId", outcome = "'CLOSED'", reason = "p.close_reason"),
            branch("PAYMENT_ATTEMPT_CREATED", "payment_attempt a where a.payment_id = :paymentId", "a.id", "a.initiated_at",
                resource = "paymentAttemptId", identity = "a.request_identity", outcome = "'CREATED'", reason = "a.risk_reason"),
            branch("PAYMENT_ATTEMPT_SUBMITTED", "payment_attempt a where a.payment_id = :paymentId and a.submitted_at is not null",
                "a.id", "a.submitted_at", resource = "paymentAttemptId", identity = "a.submission_identity", outcome = "'SUBMITTED'"),
            branch("PAYMENT_ATTEMPT_ACCEPTED", "payment_attempt a where a.payment_id = :paymentId and a.accepted_at is not null",
                "a.id", "a.accepted_at", resource = "paymentAttemptId", identity = "a.request_identity", outcome = "'ACCEPTED'"),
            branch("PAYMENT_ATTEMPT_RESULT_UNKNOWN", "payment_attempt a where a.payment_id = :paymentId and a.status = 3 and a.completed_at is not null",
                "a.id", "a.completed_at", resource = "paymentAttemptId", identity = "a.request_identity", outcome = "'RESULT_UNKNOWN'"),
            branch("PAYMENT_ATTEMPT_TERMINAL", "payment_attempt a where a.payment_id = :paymentId and a.final_result is not null and a.status in (4,5,6)",
                "a.id", "coalesce(a.notification_first_received_at,a.completed_at)", "a.result_occurred_at",
                "paymentAttemptId", identity = "a.request_identity",
                outcome = enumName("a.final_result", "SUCCESS", "FAILED", "GATEWAY_REJECTED", "RESULT_UNKNOWN")),
            branch("PAYMENT_SUBMISSION_RECEIPT", "payment_submission_receipt r join payment_attempt a on a.id = r.payment_attempt_id where a.payment_id = :paymentId",
                "r.id", "r.created_at", resource = "paymentSubmissionReceiptId", identity = "r.submission_identity",
                outcome = "r.outcome", reason = "r.diagnostic_summary", evidence = "r.channel_reference"),
            branch("PAYMENT_RESULT_RECEIPT", "payment_notification_receipt r join payment_attempt a on a.id = r.payment_attempt_id where a.payment_id = :paymentId",
                "r.id", "r.first_received_at", "r.occurred_at", "paymentResultReceiptId",
                identity = "r.notification_identity", outcome = "r.result", reason = "coalesce(r.conflict_summary,r.rejection_summary,r.verdict_summary)",
                evidence = "r.payload_identity", amount = "r.amount", currency = "r.currency"),
            branch("PAYMENT_REVIEW", "payment_review_case r where r.payment_id = :paymentId", "r.id", "r.opened_at",
                resource = "paymentReviewId", identity = "r.review_identity", outcome = enumName("r.status", "OPEN", "RESOLVED"), reason = "r.summary"),
            branch("PAYMENT_REVIEW_DECISION", "payment_review_decision d join payment_review_case r on r.id = d.payment_review_case_id where r.payment_id = :paymentId",
                "d.id", "d.decided_at", resource = "paymentReviewDecisionId", identity = "d.decision_identity",
                outcome = enumName("d.decision", "SYSTEM_ACCEPT_SUCCESS", "SYSTEM_CONFIRM_FAILURE", "ACCEPT_LATE_SUCCESS",
                    "CONFIRM_FAILURE", "KEEP_CURRENT_TERMINAL", "KEEP_ACCEPTED_SUCCESS_WITH_REMEDIATION"),
                actor = "d.operator_identity", reason = "d.reason", evidence = "d.evidence"),
            branch("REFUND_REQUESTED", "refund r where r.payment_id = :paymentId", "r.id", "r.requested_at",
                resource = "refundId", identity = "r.idempotency_key", outcome = "'REQUESTED'", reason = "r.reason",
                amount = "r.amount", currency = "r.currency"),
            branch("REFUND_BUDGET_RESERVED", "refund r where r.payment_id = :paymentId", "r.id", "r.requested_at",
                resource = "refundId", identity = "r.merchant_refund_number", outcome = "'RESERVED'",
                amount = "r.amount", currency = "r.currency"),
            branch("REFUND_BUDGET_RELEASED", "refund r where r.payment_id = :paymentId and r.reservation_released = true",
                "r.id", "coalesce(r.finalized_at,r.updated_at)", resource = "refundId", outcome = "'RELEASED'",
                amount = "r.amount", currency = "r.currency"),
            branch("REFUND_BUDGET_CONVERTED", "refund r where r.payment_id = :paymentId and r.reservation_converted_to_success = true",
                "r.id", "coalesce(r.finalized_at,r.updated_at)", resource = "refundId", outcome = "'SUCCEEDED'",
                amount = "r.amount", currency = "r.currency"),
            branch("REFUND_ATTEMPT_CREATED", "refund_attempt a join refund r on r.id = a.refund_id where r.payment_id = :paymentId",
                "a.id", "a.initiated_at", resource = "refundAttemptId", identity = "a.request_identity", outcome = "'CREATED'"),
            branch("REFUND_ATTEMPT_ACCEPTED", "refund_attempt a join refund r on r.id = a.refund_id where r.payment_id = :paymentId and a.accepted_at is not null",
                "a.id", "a.accepted_at", resource = "refundAttemptId", identity = "a.request_identity", outcome = "'ACCEPTED'"),
            branch("REFUND_RESULT_RECEIPT", "refund_notification_receipt n join refund_attempt a on a.id = n.refund_attempt_id join refund r on r.id = a.refund_id where r.payment_id = :paymentId",
                "n.id", "n.first_received_at", "n.occurred_at", "refundResultReceiptId", identity = "n.notification_identity",
                outcome = "n.result", reason = "coalesce(n.conflict_summary,n.rejection_summary,n.verdict_summary)",
                amount = "n.amount", currency = "n.currency"),
            branch("REFUND_ATTEMPT_RESULT", "refund_attempt a join refund r on r.id = a.refund_id where r.payment_id = :paymentId and a.final_result is not null",
                "a.id", "coalesce(a.notification_first_received_at,a.updated_at)", "a.result_occurred_at", "refundAttemptId",
                identity = "a.request_identity", outcome = enumName("a.final_result", "SUCCESS", "FAILED", "GATEWAY_REJECTED")),
            branch("RECONCILIATION_ITEM", "reconciliation_item ri where $LINKED_ITEM", "ri.id", "ri.created_at",
                "coalesce(ri.channel_occurred_at,ri.platform_occurred_at)", "reconciliationItemId", identity = "ri.difference_identity",
                outcome = enumName("ri.difference_type", "MATCHED", "PLATFORM_ONLY", "CHANNEL_ONLY", "AMOUNT_MISMATCH",
                    "CURRENCY_MISMATCH", "STATUS_MISMATCH", "DUPLICATE_CHANNEL_RECORD", "UNMATCHED"), reason = "ri.matching_basis",
                evidence = "ri.channel_record_identity", amount = "coalesce(ri.channel_amount,ri.platform_amount)",
                currency = "coalesce(ri.channel_currency,ri.platform_currency)"),
            branch("RECONCILIATION_BATCH",
                "reconciliation_batch rb where exists (select 1 from reconciliation_run rr where rr.batch_id = rb.id and $LINKED_RUN)",
                "rb.id", "rb.created_at", resource = "reconciliationBatchId",
                identity = "rb.channel_id", outcome = enumName("rb.status", "PENDING", "FETCHING", "RECONCILING",
                    "AWAITING_DISPOSITION", "COMPLETED", "FETCH_FAILED", "REVIEW_REQUIRED"),
                reason = "rb.blocking_reason", currency = "rb.currency"),
            branch("RECONCILIATION_RUN", "reconciliation_run rr where $LINKED_RUN", "rr.id", "rr.started_at",
                resource = "reconciliationRunId", identity = "rr.statement_identity",
                outcome = enumName("rr.status", "FETCHING", "RECONCILING", "COMPLETED", "FAILED", "SUPERSEDED"),
                reason = "rr.failure_summary"),
            branch("RECONCILIATION_DISPOSITION", "reconciliation_disposition d join reconciliation_item ri on ri.id = d.reconciliation_item_id where $LINKED_ITEM",
                "d.id", "d.disposed_at", resource = "reconciliationDispositionId",
                outcome = enumName("d.conclusion", "ACCEPT_AS_MATCHED", "CONFIRM_PLATFORM_FACT", "ACCEPT_CHANNEL_FACT",
                    "NO_SETTLEMENT_IMPACT", "ESCALATE"),
                actor = "d.operator_identity", reason = "d.reason", evidence = "d.evidence"),
            branch("RECONCILIATION_CONFIRMATION", "reconciliation_confirmation_fact c join reconciliation_item ri on ri.id = c.reconciliation_item_id where $LINKED_ITEM",
                "c.id", "c.confirmed_at", resource = "reconciliationConfirmationId", identity = "c.external_transaction_identity",
                actor = "c.operator_identity", reason = "c.confirmation_reason", evidence = "c.evidence",
                amount = "c.amount", currency = "c.currency"),
            branch("AUTHORITATIVE_BILL",
                "authoritative_bill b where exists (select 1 from bill_revision br join reconciliation_run rr on rr.statement_identity = b.bill_identity and rr.statement_revision = br.revision join reconciliation_batch rb on rb.id = rr.batch_id and rb.channel_id = b.channel_id and rb.currency = b.currency and rb.reconciliation_date = b.business_date where br.authoritative_bill_id = b.id and $LINKED_RUN)",
                "b.id", "b.created_at", resource = "billId", identity = "b.bill_identity", outcome = "b.current_revision"),
            branch("BILL_REVISION",
                "bill_revision br join authoritative_bill b on b.id = br.authoritative_bill_id where exists (select 1 from reconciliation_run rr join reconciliation_batch rb on rb.id = rr.batch_id and rb.channel_id = b.channel_id and rb.currency = b.currency and rb.reconciliation_date = b.business_date where rr.statement_identity = b.bill_identity and rr.statement_revision = br.revision and $LINKED_RUN)",
                "br.id", "br.created_at", "br.published_at", "billRevisionId", identity = "br.revision",
                outcome = enumName("br.completeness", "UNKNOWN", "INCOMPLETE", "COMPLETE"), evidence = "br.payload_fingerprint"),
            branch("BILL_AVAILABLE",
                "bill_available_signal sig join authoritative_bill b on b.id = sig.authoritative_bill_id where exists (select 1 from reconciliation_run rr join reconciliation_batch rb on rb.id = rr.batch_id and rb.channel_id = b.channel_id and rb.currency = b.currency and rb.reconciliation_date = b.business_date where rr.statement_identity = b.bill_identity and rr.statement_revision = sig.announced_revision and $LINKED_RUN)",
                "sig.id", "sig.received_at", "sig.published_at", "billAvailableSignalId",
                identity = "sig.signal_identity", outcome = "'AVAILABLE'", reason = "sig.diagnostic"),
            branch("BILL_REVISION_RECORD",
                "bill_revision_record rec join bill_revision br on br.id = rec.bill_revision_id join authoritative_bill b on b.id = br.authoritative_bill_id where exists (select 1 from reconciliation_run rr join reconciliation_batch rb on rb.id = rr.batch_id and rb.channel_id = b.channel_id and rb.currency = b.currency and rb.reconciliation_date = b.business_date where rr.statement_identity = b.bill_identity and rr.statement_revision = br.revision and $LINKED_RUN) and exists (select 1 from reconciliation_item ri join reconciliation_run rr2 on rr2.id = ri.reconciliation_run_id where rr2.statement_identity = b.bill_identity and rr2.statement_revision = br.revision and ri.channel_record_identity = rec.record_identity and $LINKED_ITEM)",
                "rec.id", "rec.received_at", "rec.occurred_at", "billRevisionRecordId",
                identity = "rec.record_identity", outcome = "rec.raw_status", evidence = "rec.raw_evidence",
                amount = "rec.amount", currency = "rec.currency"),
            branch("SETTLEMENT_ITEM", "settlement_line sl where $LINKED_LINE", "sl.id", "sl.recorded_at", "sl.occurred_at",
                "settlementLineId", identity = "sl.source_fact_identity", outcome = "sl.decision", reason = "sl.reason_code",
                evidence = "sl.eligibility_basis", amount = "sl.signed_net_amount", currency = "sl.currency"),
            branch("SETTLEMENT_PREPARED", "merchant_settlement s where $LINKED_SETTLEMENT", "s.id", "s.created_at",
                resource = "settlementId", identity = "s.scope_identity", outcome = "'PREPARED'", amount = "s.net_amount", currency = "s.currency"),
            branch("SETTLEMENT_CONFIRMED", "merchant_settlement s where $LINKED_SETTLEMENT and s.confirmed_at is not null",
                "s.id", "s.confirmed_at", resource = "settlementId", actor = "s.confirmed_by", outcome = "'CONFIRMED'"),
            branch("SETTLEMENT_VOIDED", "merchant_settlement s where $LINKED_SETTLEMENT and s.voided_at is not null",
                "s.id", "s.voided_at", resource = "settlementId", actor = "s.voided_by", outcome = "'VOIDED'", reason = "s.void_reason"),
            branch("SETTLEMENT_SUCCEEDED", "merchant_settlement s where $LINKED_SETTLEMENT and s.settled_fact_formed = true and s.completed_at is not null",
                "s.id", "s.completed_at", resource = "settlementId", identity = "s.external_settlement_identity",
                outcome = "'SUCCEEDED'", amount = "s.net_amount", currency = "s.currency"),
            branch("SETTLEMENT_REPLACEMENT", "merchant_settlement s where $LINKED_SETTLEMENT and s.predecessor_settlement_id is not null",
                "s.id", "s.created_at", resource = "settlementId", identity = "s.predecessor_settlement_id", outcome = "'REPLACEMENT'"),
            branch("SETTLEMENT_EXECUTION", "settlement_execution_attempt a join merchant_settlement s on s.id = a.merchant_settlement_id where $LINKED_SETTLEMENT",
                "a.id", "a.initiated_at", resource = "executionId", resourceId = "a.execution_id", identity = "a.execution_id",
                outcome = enumName("a.status", "PROCESSING", "SUCCEEDED", "FAILED", "RESULT_UNKNOWN",
                    "REVIEW_REQUIRED", "CONFLICT_REVIEW_REQUIRED"), reason = "a.verdict_summary",
                amount = "a.amount", currency = "a.currency"),
            branch("SETTLEMENT_RESULT_RECEIPT", "settlement_result_receipt r join settlement_execution_attempt a on a.id = r.settlement_execution_attempt_id join merchant_settlement s on s.id = a.merchant_settlement_id where $LINKED_SETTLEMENT",
                "r.id", "r.first_received_at", "r.occurred_at", "executionId", resourceId = "a.execution_id", identity = "a.execution_id",
                outcome = "r.result", reason = "coalesce(r.conflict_summary,r.rejection_summary,r.verdict_summary)",
                evidence = "r.payload_fingerprint", amount = "r.amount", currency = "r.currency"),
            branch("MERCHANT_NOTIFICATION", "merchant_notification n where n.payment_id = :paymentId",
                "n.id", "n.created_at", resource = "notificationId", identity = "n.content_identity",
                outcome = enumName("n.status", "PENDING", "DELIVERED", "FAILED", "RESULT_UNKNOWN"), evidence = "n.source_fact_identity"),
            branch("MERCHANT_NOTIFICATION_DELIVERY", "merchant_notification_delivery_attempt a join merchant_notification n on n.id = a.merchant_notification_id where n.payment_id = :paymentId",
                "a.id", "a.recorded_at", resource = "notificationDeliveryAttemptId", identity = "a.delivery_identity",
                outcome = enumName("a.outcome", "SUCCESS", "FAILURE", "RESULT_UNKNOWN"),
                reason = "a.diagnostic", evidence = "a.content_identity"),
            branch("MANUAL_REVIEW", "manual_review_item m where m.payment_id = :paymentId",
                "m.id", "m.created_at", resource = "manualReviewId", identity = "m.review_identity",
                outcome = "m.status", reason = "m.summary", evidence = "m.evidence_refs_json"),
            branch("MANUAL_REVIEW_RESOLUTION", "manual_review_resolution r join manual_review_item m on m.id = r.manual_review_item_id where m.payment_id = :paymentId",
                "r.id", "r.resolved_at", resource = "manualReviewResolutionId", identity = "r.resolution_identity",
                outcome = "r.outcome", actor = "r.actor_id", reason = "r.reason", evidence = "r.evidence"),
            branch("OPERATION_ACCEPTED", "operation o where (o.resource_type = 'PaymentIntent' and o.resource_id = :paymentId) or (o.resource_type = 'PaymentAttempt' and o.resource_id in (select a.id from payment_attempt a where a.payment_id = :paymentId)) or (o.resource_type = 'Refund' and o.resource_id in (select r.id from refund r where r.payment_id = :paymentId)) or (o.resource_type = 'RefundAttempt' and o.resource_id in (select a.id from refund_attempt a join refund r on r.id = a.refund_id where r.payment_id = :paymentId)) or (o.resource_type = 'MerchantNotification' and o.resource_id in (select n.id from merchant_notification n where n.payment_id = :paymentId)) or (o.resource_type = 'ManualReviewItem' and o.resource_id in (select m.id from manual_review_item m where m.payment_id = :paymentId))",
                "o.id", "o.accepted_at", resource = "operationId", identity = "o.idempotency_key", outcome = "o.status",
                evidence = "o.command_type"),
        )

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

        fun asInstant(value: Any): Instant = when (value) {
            is Instant -> value
            is OffsetDateTime -> value.toInstant()
            is LocalDateTime -> value.toInstant(ZoneOffset.UTC)
            // CAP4K persists aggregate timestamps as UTC-valued LocalDateTime. Native queries
            // expose those columns as java.sql.Timestamp, whose toInstant() interprets the value
            // in the process/JDBC timezone (Asia/Shanghai in the reference runtime) and shifts it
            // by eight hours. Preserve the persisted UTC wall-clock value explicitly.
            is Timestamp -> value.toLocalDateTime().toInstant(ZoneOffset.UTC)
            is java.util.Date -> value.toInstant()
            else -> Instant.parse(value.toString())
        }
    }
}
