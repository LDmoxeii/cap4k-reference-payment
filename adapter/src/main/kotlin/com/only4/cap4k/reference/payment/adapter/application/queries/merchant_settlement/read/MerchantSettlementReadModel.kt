package com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read

import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.adapter.application.queries.ReferenceCursorToken
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ListMerchantSettlementsEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.domain.values.Money as DomainMoney
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service

/**
 * Authoritative SQL projection for Settlement pages.  It only reads the settlement root and the
 * latest execution status needed by the collection, leaving item/execution evidence to detail APIs.
 */
@Service
class MerchantSettlementReadModel(
    private val entityManager: EntityManager,
    private val referencePolicy: ReferencePolicyService,
) {
    fun list(request: ListMerchantSettlementsEndpoint.Request): ListMerchantSettlementsEndpoint.Response {
        val pageSize = referencePolicy.pageSize(request.pageSize)
        val filter = Filter.from(request)
        val cursor = request.cursor?.trim()?.takeIf(String::isNotEmpty)?.let { Cursor.decode(it, filter.fingerprint) }
        val clauses = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        fun where(clause: String, vararg values: Any) {
            clauses += clause
            parameters.addAll(values)
        }

        filter.merchantId?.let { where("s.merchant_id = ?", it) }
        filter.status?.let { where(statusClause(it)) }
        filter.finality?.let { where(finalityClause(it)) }
        filter.settlementId?.let { where("s.id = ?", it) }
        filter.currency?.let { where("s.currency = ?", it) }
        filter.periodStart?.let { where("s.period_start >= ?", it) }
        filter.periodEnd?.let { where("s.period_end <= ?", it) }
        filter.businessTimezone?.let { where("s.business_timezone = ?", it) }
        filter.executionStatus?.let { executionStatus ->
            where(
                "exists (select 1 from settlement_execution_attempt sea where sea.merchant_settlement_id = s.id " +
                    "and sea.status in (${executionStatusToDatabase(executionStatus).joinToString(",") { it.toString() }}))",
            )
        }
        filter.createdFrom?.let { where("s.created_at >= ?", it) }
        filter.createdTo?.let { where("s.created_at <= ?", it) }
        cursor?.let { where("(s.created_at < ? or (s.created_at = ? and s.id < ?))", it.sortTime, it.sortTime, it.id) }

        val sql = buildString {
            append("select s.id, s.merchant_id, s.currency, s.period_start, s.period_end, s.business_timezone, s.status, s.created_at, ")
            append("s.payment_gross_amount, s.refund_gross_amount, s.fee_total_amount, s.adjustment_total_amount, s.net_amount, ")
            append("s.composition_frozen, s.predecessor_settlement_id, s.replacement_settlement_id, ")
            append("(select sea.status from settlement_execution_attempt sea where sea.merchant_settlement_id = s.id ")
            append("order by sea.attempt_sequence desc limit 1) ")
            append("from merchant_settlement s ")
            if (clauses.isNotEmpty()) append("where ").append(clauses.joinToString(" and ")).append(' ')
            append("order by s.created_at desc, s.id desc limit ?")
        }
        val query = entityManager.createNativeQuery(sql)
        parameters.forEachIndexed { index, value -> query.setParameter(index + 1, value) }
        query.setParameter(parameters.size + 1, pageSize + 1)
        val rows = query.resultList.map { it as Array<Any?> }
        val items = rows.take(pageSize).map(::toItem)
        return ListMerchantSettlementsEndpoint.Response(
            items = items,
            nextCursor = if (rows.size > pageSize) {
                items.lastOrNull()?.let { Cursor.encode(filter.fingerprint, it.sortTime, it.settlementId) }
            } else {
                null
            },
            pageSize = pageSize,
        )
    }

    private fun toItem(row: Array<Any?>): ListMerchantSettlementsEndpoint.Response.Item {
        val status = status(row[6])
        val currency = row[2].text("currency").uppercase()
        return ListMerchantSettlementsEndpoint.Response.Item(
            settlementId = row[0].text("settlementId"),
            merchantId = row[1].text("merchantId"),
            currency = currency,
            periodStart = asInstant(row[3] ?: error("结算单缺少 periodStart")),
            periodEnd = asInstant(row[4] ?: error("结算单缺少 periodEnd")),
            businessTimezone = row[5].text("businessTimezone"),
            status = MerchantSettlementContractStatus.publicStatus(status),
            finality = MerchantSettlementContractStatus.finality(status),
            sortTime = asInstant(row[7] ?: error("结算单缺少 sortTime")),
            grossMoney = money(row[8], currency),
            refundMoney = money(row[9], currency),
            feeMoney = money(row[10], currency),
            adjustmentMoney = money(row[11], currency),
            netMoney = money(row[12], currency),
            compositionFrozen = row[13] as Boolean,
            predecessorSettlementId = row[14]?.toString(),
            replacementSettlementId = row[15]?.toString(),
            executionStatus = row[16]?.let(::executionStatus),
        )
    }

    private fun finalityClause(finality: Finality): String = when (finality) {
        Finality.REVIEW_REQUIRED -> "s.status in (1, 8, 10)"
        Finality.FINAL -> "s.status in (5, 9)"
        Finality.NON_FINAL -> "s.status not in (1, 5, 8, 9, 10)"
    }

    private fun statusClause(status: String): String = when (status) {
        "PREPARING" -> "s.status = 0"
        "READY_FOR_CONFIRMATION" -> "s.status in (1, 2, 8)"
        "CONFIRMED" -> "s.status = 3"
        "EXECUTING" -> "s.status = 4"
        "SETTLED" -> "s.status = 5"
        "EXECUTION_FAILED" -> "s.status = 6"
        "RESULT_UNKNOWN" -> "s.status in (7, 10)"
        "VOIDED" -> "s.status = 9"
        else -> throw IllegalArgumentException("不支持的 Settlement status")
    }

    private fun executionStatus(value: Any?): String = MerchantSettlementContractStatus.publicExecutionStatus(
        SettlementExecutionAttemptStatus.valueOfOrNull((value as Number).toInt())
            ?: throw IllegalStateException("结算执行状态投影无效")
    )

    private fun status(value: Any?): MerchantSettlementStatus = MerchantSettlementStatus.valueOfOrNull((value as Number).toInt())
        ?: throw IllegalStateException("结算单状态投影无效")

    private data class Filter(
        val merchantId: String?,
        val status: String?,
        val finality: Finality?,
        val settlementId: String?,
        val currency: String?,
        val periodStart: Instant?,
        val periodEnd: Instant?,
        val businessTimezone: String?,
        val executionStatus: String?,
        val createdFrom: Instant?,
        val createdTo: Instant?,
    ) {
        val fingerprint: String = sha256(
            listOf(
                merchantId, status, finality?.name, settlementId, currency, periodStart?.toString(), periodEnd?.toString(),
                businessTimezone, executionStatus, createdFrom?.toString(), createdTo?.toString(),
            ).joinToString("|") { it.orEmpty() },
        )

        companion object {
            fun from(request: ListMerchantSettlementsEndpoint.Request): Filter = Filter(
                merchantId = request.merchantId.normalized(),
                status = request.status.normalized()?.uppercase(),
                finality = request.finality,
                settlementId = request.settlementId.normalized(),
                currency = request.currency.normalized()?.uppercase(),
                periodStart = request.periodStart,
                periodEnd = request.periodEnd,
                businessTimezone = request.businessTimezone.normalized(),
                executionStatus = request.executionStatus.normalized()?.uppercase()?.also(::executionStatusToDatabase),
                createdFrom = request.createdFrom,
                createdTo = request.createdTo,
            ).also {
                require(it.periodStart == null || it.periodEnd == null || !it.periodStart.isAfter(it.periodEnd)) {
                    "periodStart 不能晚于 periodEnd"
                }
                require(it.createdFrom == null || it.createdTo == null || !it.createdFrom.isAfter(it.createdTo)) {
                    "createdFrom 不能晚于 createdTo"
                }
            }
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
        fun executionStatusToDatabase(status: String): List<Int> = when (status) {
            "SUBMITTED" -> listOf(SettlementExecutionAttemptStatus.PROCESSING.value)
            "SUCCESS" -> listOf(SettlementExecutionAttemptStatus.SUCCEEDED.value)
            "FAILURE" -> listOf(SettlementExecutionAttemptStatus.FAILED.value)
            "UNKNOWN" -> listOf(
                SettlementExecutionAttemptStatus.RESULT_UNKNOWN.value,
                SettlementExecutionAttemptStatus.REVIEW_REQUIRED.value,
                SettlementExecutionAttemptStatus.CONFLICT_REVIEW_REQUIRED.value,
            )
            else -> throw IllegalArgumentException("不支持的 Settlement executionStatus")
        }

        fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
        fun Any?.text(field: String): String = this?.toString()?.takeIf { it.isNotBlank() }
            ?: error("结算单缺少 $field")
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
        fun money(value: Any?, currency: String): Money {
            val amount = when (value) {
                is BigDecimal -> value
                is Number -> BigDecimal(value.toString())
                else -> BigDecimal(value?.toString() ?: error("结算单缺少金额"))
            }
            val minor = amount.movePointRight(DomainMoney.fractionDigits(currency)).toBigIntegerExact().toString()
            return Money(currency = currency, amountMinor = minor)
        }
    }
}
