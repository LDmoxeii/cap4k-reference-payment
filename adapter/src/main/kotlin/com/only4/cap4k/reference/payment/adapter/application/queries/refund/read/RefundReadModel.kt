package com.only4.cap4k.reference.payment.adapter.application.queries.refund.read

import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.adapter.application.queries.ReferenceCursorToken
import com.only4.cap4k.reference.payment.adapter.contract.ReferenceContractStatusMapper
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.ListRefundsEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
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

/** Native SQL read model for the authoritative Refund collection and its stable keyset pages. */
@Service
class RefundReadModel(
    private val entityManager: EntityManager,
    private val referencePolicy: ReferencePolicyService,
) {
    fun list(request: ListRefundsEndpoint.Request): ListRefundsEndpoint.Response {
        val pageSize = referencePolicy.pageSize(request.pageSize)
        val filter = Filter.from(request)
        val cursor = request.cursor?.trim()?.takeIf(String::isNotEmpty)?.let { Cursor.decode(it, filter.fingerprint) }
        val clauses = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        fun where(clause: String, vararg values: Any) {
            clauses += clause
            parameters.addAll(values)
        }

        filter.merchantId?.let { where("r.merchant_id = ?", it) }
        filter.status?.let { publicStatus ->
            val internalStatuses = ReferenceContractStatusMapper.refundStatusesForFilter(publicStatus)
                .sortedBy { it.value }
            if (internalStatuses.isEmpty()) {
                where("1 = 0")
            } else {
                where(
                    "r.status in (${internalStatuses.joinToString(",") { "?" }})",
                    *internalStatuses.map { it.value }.toTypedArray(),
                )
            }
        }
        filter.finality?.let { where(finalityClause(it)) }
        filter.paymentId?.let { where("r.payment_id = ?", it) }
        filter.refundId?.let { where("r.id = ?", it) }
        filter.merchantRefundId?.let { where("r.merchant_refund_number = ?", it) }
        filter.channelId?.let { channelId ->
            where("exists (select 1 from refund_attempt ra where ra.refund_id = r.id and ra.channel_id = ?)", channelId)
        }
        filter.currency?.let { where("r.currency = ?", it) }
        filter.createdFrom?.let { where("r.created_at >= ?", it) }
        filter.createdTo?.let { where("r.created_at <= ?", it) }
        cursor?.let { where("(r.created_at < ? or (r.created_at = ? and r.id < ?))", it.sortTime, it.sortTime, it.id) }

        val sql = buildString {
            append("select r.id, r.payment_id, r.merchant_id, r.merchant_refund_number, r.amount, r.currency, r.reason, ")
            append("r.status, r.created_at, r.channel_id, r.channel_refund_id, r.reservation_active, r.settlement_blocked ")
            append("from refund r ")
            if (clauses.isNotEmpty()) append("where ").append(clauses.joinToString(" and ")).append(' ')
            append("order by r.created_at desc, r.id desc limit ?")
        }
        val query = entityManager.createNativeQuery(sql)
        parameters.forEachIndexed { index, value -> query.setParameter(index + 1, value) }
        query.setParameter(parameters.size + 1, pageSize + 1)
        val rows = query.resultList.map { it as Array<Any?> }
        val items = rows.take(pageSize).map(::toItem)
        return ListRefundsEndpoint.Response(
            items = items,
            nextCursor = if (rows.size > pageSize) {
                items.lastOrNull()?.let { Cursor.encode(filter.fingerprint, it.sortTime, it.refundId) }
            } else {
                null
            },
            pageSize = pageSize,
        )
    }

    private fun toItem(row: Array<Any?>): ListRefundsEndpoint.Response.Item {
        val status = status(row[7])
        val settlementBlocked = row[12] as Boolean
        val currency = row[5].text("currency").uppercase()
        return ListRefundsEndpoint.Response.Item(
            refundId = row[0].text("refundId"),
            paymentId = row[1].text("paymentId"),
            merchantId = row[2].text("merchantId"),
            merchantRefundId = row[3].text("merchantRefundId"),
            money = money(row[4], currency),
            reason = row[6].text("reason"),
            status = ReferenceContractStatusMapper.refundStatus(status),
            finality = ReferenceContractStatusMapper.refundFinality(status, settlementBlocked),
            sortTime = asInstant(row[8] ?: error("退款缺少 sortTime")),
            channelId = row[9]?.toString(),
            channelRefundId = row[10]?.toString(),
            reservationActive = row[11] as Boolean,
            settlementBlocked = settlementBlocked,
        )
    }

    private fun finalityClause(finality: Finality): String {
        val review = "(r.status = 4 or r.settlement_blocked = true)"
        return when (finality) {
            Finality.REVIEW_REQUIRED -> review
            Finality.FINAL -> "(r.status in (1, 2) and not $review)"
            Finality.NON_FINAL -> "(r.status in (0, 3, 5) and not $review)"
        }
    }

    private fun status(value: Any?): RefundStatus = RefundStatus.valueOfOrNull((value as Number).toInt())
        ?: throw IllegalStateException("退款状态投影无效")

    private data class Filter(
        val merchantId: String?,
        val status: String?,
        val finality: Finality?,
        val paymentId: String?,
        val refundId: String?,
        val merchantRefundId: String?,
        val channelId: String?,
        val currency: String?,
        val createdFrom: Instant?,
        val createdTo: Instant?,
    ) {
        val fingerprint: String = sha256(
            listOf(merchantId, status, finality?.name, paymentId, refundId, merchantRefundId, channelId, currency, createdFrom?.toString(), createdTo?.toString())
                .joinToString("|") { it.orEmpty() },
        )

        companion object {
            fun from(request: ListRefundsEndpoint.Request): Filter = Filter(
                merchantId = request.merchantId.normalized(),
                status = request.status.normalized()?.uppercase()?.also {
                    ReferenceContractStatusMapper.refundStatusesForFilter(it)
                },
                finality = request.finality,
                paymentId = request.paymentId.normalized(),
                refundId = request.refundId.normalized(),
                merchantRefundId = request.merchantRefundId.normalized(),
                channelId = request.channelId.normalized(),
                currency = request.currency.normalized()?.uppercase(),
                createdFrom = request.createdFrom,
                createdTo = request.createdTo,
            ).also {
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
        fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
        fun Any?.text(field: String): String = this?.toString()?.takeIf { it.isNotBlank() }
            ?: error("退款缺少 $field")
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
                else -> BigDecimal(value?.toString() ?: error("退款缺少 amount"))
            }
            val minor = amount.movePointRight(DomainMoney.fractionDigits(currency)).toBigIntegerExact().toString()
            return Money(currency = currency, amountMinor = minor)
        }
    }
}
