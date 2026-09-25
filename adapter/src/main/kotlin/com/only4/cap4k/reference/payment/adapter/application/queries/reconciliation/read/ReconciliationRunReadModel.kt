package com.only4.cap4k.reference.payment.adapter.application.queries.reconciliation.read

import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.adapter.application.queries.ReferenceCursorToken
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.ListReconciliationRunsEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationRunStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service

/**
 * Read-only SQL projection for the authoritative ReconciliationRun collection.  It deliberately
 * queries the run tables instead of materialising ReconciliationBatch aggregates in an endpoint.
 */
@Service
class ReconciliationRunReadModel(
    private val entityManager: EntityManager,
    private val referencePolicy: ReferencePolicyService,
) {
    fun batchIdFor(runId: String): String = entityManager.createNativeQuery(
        "select batch_id from reconciliation_run where id = ?1",
    ).setParameter(1, runId.trim()).resultList.firstOrNull()?.toString()
        ?: throw IllegalArgumentException("未找到对账运行：$runId")

    fun requireRunItem(runId: String, itemId: String) {
        val count = (entityManager.createNativeQuery(
            "select count(*) from reconciliation_item where reconciliation_run_id = ?1 and id = ?2",
        ).setParameter(1, runId.trim()).setParameter(2, itemId.trim()).singleResult as Number).toLong()
        require(count == 1L) { "对账差异不属于指定运行" }
    }

    fun createdAtFor(runId: String): Instant = asInstant(
        entityManager.createNativeQuery("select created_at from reconciliation_run where id = ?1")
            .setParameter(1, runId.trim())
            .resultList
            .firstOrNull()
            ?: throw IllegalArgumentException("未找到对账运行：$runId"),
    )

    fun list(request: ListReconciliationRunsEndpoint.Request): ListReconciliationRunsEndpoint.Response {
        val pageSize = referencePolicy.pageSize(request.pageSize)
        val filter = Filter.from(request)
        val cursor = request.cursor?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Cursor.decode(it, filter.fingerprint)
        }
        val clauses = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        fun where(clause: String, vararg values: Any) {
            clauses += clause
            parameters.addAll(values)
        }

        filter.runId?.let { where("r.id = ?", it) }
        filter.channelId?.let { where("b.channel_id = ?", it) }
        filter.billId?.let { where("ab.id = ?", it) }
        filter.billRevision?.let { where("r.statement_revision = ?", it) }
        filter.businessDate?.let { where("b.reconciliation_date = ?", it) }
        filter.currency?.let { where("b.currency = ?", it) }
        filter.effectiveRun?.let { effective ->
            where(if (effective) "b.current_effective_run_id = r.id" else "(b.current_effective_run_id is null or b.current_effective_run_id <> r.id)")
        }
        filter.createdFrom?.let { where("r.created_at >= ?", it) }
        filter.createdTo?.let { where("r.created_at <= ?", it) }
        filter.status?.let { status ->
            when (status) {
                "ACTION_REQUIRED" -> where("b.status in (3, 6)")
                else -> where("r.status = ?", ReconciliationRunStatus.valueOf(status).value)
            }
        }
        filter.finality?.let { finality ->
            when (finality) {
                Finality.REVIEW_REQUIRED -> where("b.status in (3, 6)")
                Finality.NON_FINAL -> where("r.status in (0, 1) and b.status not in (3, 6)")
                Finality.FINAL -> where("r.status in (2, 3, 4) and b.status not in (3, 6)")
            }
        }
        filter.merchantId?.let { merchantId ->
            where(
                "exists (select 1 from reconciliation_item ri " +
                    "left join payment p on p.id = ri.payment_id " +
                    "left join refund rf on rf.id = ri.refund_id " +
                    "left join reconciliation_confirmation_fact cf on cf.reconciliation_item_id = ri.id " +
                    "where ri.reconciliation_run_id = r.id and (p.merchant_id = ? or rf.merchant_id = ? or cf.merchant_id = ?))",
                merchantId,
                merchantId,
                merchantId,
            )
        }
        cursor?.let { where("(r.created_at < ? or (r.created_at = ? and r.id < ?))", it.sortTime, it.sortTime, it.id) }

        val sql = buildString {
            append("select r.id, b.channel_id, b.currency, b.reconciliation_date, ab.id, r.statement_revision, ")
            append("r.status, b.status, b.current_effective_run_id, r.created_at, r.completed_at, r.matched_count, ")
            append("r.difference_count, r.unresolved_difference_count, b.settlement_blocked ")
            append("from reconciliation_run r join reconciliation_batch b on b.id = r.batch_id ")
            append("join authoritative_bill ab on ab.channel_id = b.channel_id and ab.bill_identity = r.statement_identity ")
            if (clauses.isNotEmpty()) append("where ").append(clauses.joinToString(" and ")).append(' ')
            append("order by r.created_at desc, r.id desc limit ?")
        }
        val nativeQuery = entityManager.createNativeQuery(sql)
        parameters.forEachIndexed { index, value -> nativeQuery.setParameter(index + 1, value) }
        nativeQuery.setParameter(parameters.size + 1, pageSize + 1)
        val rows = nativeQuery.resultList.map { it as Array<Any?> }
        val hasNext = rows.size > pageSize
        val items = rows.take(pageSize).map(::toItem)
        val nextCursor = if (hasNext) items.lastOrNull()?.let { Cursor.encode(filter.fingerprint, it.sortTime, it.runId) } else null
        return ListReconciliationRunsEndpoint.Response(
            items = items,
            nextCursor = nextCursor,
            pageSize = pageSize,
        )
    }

    private fun toItem(row: Array<Any?>): ListReconciliationRunsEndpoint.Response.Item {
        val runStatus = status(row[6])
        val batchStatus = (row[7] as Number).toInt()
        val runId = row[0].toString()
        val effective = row[8]?.toString() == runId
        val finality = finality(runStatus, batchStatus)
        return ListReconciliationRunsEndpoint.Response.Item(
            runId = runId,
            channelId = row[1].toString(),
            currency = row[2].toString(),
            businessDate = asLocalDate(requireNotNull(row[3]) { "对账运行缺少 businessDate" }),
            billId = row[4].toString(),
            billRevision = row[5].toString(),
            status = publicStatus(runStatus, batchStatus, effective),
            finality = finality,
            effectiveRun = effective,
            sortTime = asInstant(requireNotNull(row[9]) { "对账运行缺少 sortTime" }),
            completedAt = row[10]?.let(::asInstant),
            matchedCount = (row[11] as Number).toInt(),
            differenceCount = (row[12] as Number).toInt(),
            unresolvedDifferenceCount = (row[13] as Number).toInt(),
            settlementBlocked = row[14] as Boolean,
        )
    }

    fun publicStatus(runStatus: ReconciliationRunStatus, batchStatus: Int, effective: Boolean): String =
        if (effective && batchStatus in setOf(3, 6)) "ACTION_REQUIRED" else runStatus.name

    fun finality(runStatus: ReconciliationRunStatus, batchStatus: Int): Finality = when {
        batchStatus in setOf(3, 6) -> Finality.REVIEW_REQUIRED
        runStatus in setOf(ReconciliationRunStatus.COMPLETED, ReconciliationRunStatus.FAILED, ReconciliationRunStatus.SUPERSEDED) -> Finality.FINAL
        else -> Finality.NON_FINAL
    }

    private fun status(value: Any?): ReconciliationRunStatus =
        ReconciliationRunStatus.valueOfOrNull((value as Number).toInt())
            ?: throw IllegalStateException("对账运行状态投影无效")

    private data class Filter(
        val merchantId: String?,
        val status: String?,
        val finality: Finality?,
        val runId: String?,
        val channelId: String?,
        val billId: String?,
        val billRevision: String?,
        val businessDate: LocalDate?,
        val currency: String?,
        val effectiveRun: Boolean?,
        val createdFrom: Instant?,
        val createdTo: Instant?,
    ) {
        val fingerprint: String = sha256(
            listOf(
                merchantId, status, finality?.name, runId, channelId, billId, billRevision,
                businessDate?.toString(), currency, effectiveRun?.toString(), createdFrom?.toString(), createdTo?.toString(),
            ).joinToString("|") { it ?: "" },
        )

        companion object {
            fun from(request: ListReconciliationRunsEndpoint.Request): Filter = Filter(
                merchantId = request.merchantId.normalized(),
                status = request.status.normalized()?.uppercase()?.also {
                    require(it == "ACTION_REQUIRED" || runCatching { ReconciliationRunStatus.valueOf(it) }.isSuccess) {
                        "不支持的 ReconciliationRun status"
                    }
                },
                finality = request.finality,
                runId = request.runId.normalized(),
                channelId = request.channelId.normalized(),
                billId = request.billId.normalized(),
                billRevision = request.billRevision.normalized(),
                businessDate = request.businessDate,
                currency = request.currency.normalized()?.uppercase(),
                effectiveRun = request.effectiveRun,
                createdFrom = request.createdFrom,
                createdTo = request.createdTo,
            )
        }
    }

    private data class Cursor(val fingerprint: String, val sortTime: Instant, val id: String) {
        companion object {
            fun encode(fingerprint: String, sortTime: Instant, id: String): String =
                ReferenceCursorToken.encode("v1", fingerprint, sortTime.toString(), id)

            fun decode(raw: String, expectedFingerprint: String): Cursor = try {
                val fields = ReferenceCursorToken.decode(raw, 4)
                if (fields.size != 4 || fields[0] != "v1" || fields[1] != expectedFingerprint || fields[3].isBlank()) {
                    throw InvalidCursorException()
                }
                Cursor(fields[1], Instant.parse(fields[2]), fields[3])
            } catch (error: InvalidCursorException) {
                throw error
            } catch (_: Exception) {
                throw InvalidCursorException()
            }
        }
    }

    private companion object {
        fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        fun asInstant(value: Any): Instant = when (value) {
            is Instant -> value
            is OffsetDateTime -> value.toInstant()
            is LocalDateTime -> value.toInstant(ZoneOffset.UTC)
            is Timestamp -> value.toInstant()
            is java.util.Date -> value.toInstant()
            else -> Instant.parse(value.toString())
        }

        fun asLocalDate(value: Any): LocalDate = when (value) {
            is LocalDate -> value
            is java.sql.Date -> value.toLocalDate()
            else -> LocalDate.parse(value.toString())
        }
    }
}
