package com.only4.cap4k.reference.payment.adapter.application.queries.payment.read

import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.adapter.application.queries.ReferenceCursorToken
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.ListPaymentIntentsEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
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
 * SQL projection of PaymentIntent collection state.  It intentionally does not hydrate Payment
 * aggregates: a page is produced by one keyset query over authoritative persisted state.
 */
@Service
class PaymentIntentReadModel(
    private val entityManager: EntityManager,
    private val referencePolicy: ReferencePolicyService,
) {
    fun list(request: ListPaymentIntentsEndpoint.Request): ListPaymentIntentsEndpoint.Response {
        val pageSize = referencePolicy.pageSize(request.pageSize)
        val filter = Filter.from(request)
        val cursor = request.cursor?.trim()?.takeIf(String::isNotEmpty)?.let { Cursor.decode(it, filter.fingerprint) }
        val clauses = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        fun where(clause: String, vararg values: Any) {
            clauses += clause
            parameters.addAll(values)
        }

        filter.merchantId?.let { where("p.merchant_id = ?", it) }
        filter.status?.let { where("p.status = ?", statusToDatabase(it).value) }
        filter.finality?.let { where(finalityClause(it)) }
        filter.paymentId?.let { where("p.id = ?", it) }
        filter.merchantOrderId?.let { where("p.merchant_order_number = ?", it) }
        filter.channelId?.let { channelId ->
            where("exists (select 1 from payment_attempt pa where pa.payment_id = p.id and pa.channel_id = ?)", channelId)
        }
        filter.currency?.let { where("p.currency = ?", it) }
        filter.createdFrom?.let { where("p.created_at >= ?", it) }
        filter.createdTo?.let { where("p.created_at <= ?", it) }
        cursor?.let { where("(p.created_at < ? or (p.created_at = ? and p.id < ?))", it.sortTime, it.sortTime, it.id) }

        val sql = buildString {
            append("select p.id, p.merchant_id, p.merchant_order_number, p.amount, p.currency, p.payment_method, ")
            append("p.status, p.created_at, p.expires_at, p.succeeded_at, p.channel_transaction_id, ")
            append("p.attempt_count, p.settlement_blocked, p.blocking_review_count ")
            append("from payment p ")
            if (clauses.isNotEmpty()) append("where ").append(clauses.joinToString(" and ")).append(' ')
            append("order by p.created_at desc, p.id desc limit ?")
        }
        val query = entityManager.createNativeQuery(sql)
        parameters.forEachIndexed { index, value -> query.setParameter(index + 1, value) }
        query.setParameter(parameters.size + 1, pageSize + 1)
        val rows = query.resultList.map { it as Array<Any?> }
        val items = rows.take(pageSize).map(::toItem)
        return ListPaymentIntentsEndpoint.Response(
            items = items,
            nextCursor = if (rows.size > pageSize) {
                items.lastOrNull()?.let { Cursor.encode(filter.fingerprint, it.sortTime, it.paymentId) }
            } else {
                null
            },
            pageSize = pageSize,
        )
    }

    private fun toItem(row: Array<Any?>): ListPaymentIntentsEndpoint.Response.Item {
        val status = status(row[6])
        val settlementBlocked = row[12] as Boolean
        val blockingReviewCount = (row[13] as Number).toInt()
        val currency = row[4].text("currency").uppercase()
        return ListPaymentIntentsEndpoint.Response.Item(
            paymentId = row[0].text("paymentId"),
            merchantId = row[1].text("merchantId"),
            merchantOrderId = row[2].text("merchantOrderId"),
            money = money(row[3], currency),
            paymentMethod = row[5].text("paymentMethod"),
            status = publicStatus(status),
            finality = finality(status, settlementBlocked, blockingReviewCount),
            sortTime = asInstant(row[7] ?: error("支付缺少 sortTime")),
            expiresAt = asInstant(row[8] ?: error("支付缺少 expiresAt")),
            succeededAt = row[9]?.let(::asInstant),
            channelTransactionId = row[10]?.toString(),
            attemptCount = (row[11] as Number).toInt(),
            settlementBlocked = settlementBlocked,
        )
    }

    private fun finalityClause(finality: Finality): String {
        val review = "(p.settlement_blocked = true or p.blocking_review_count > 0)"
        return when (finality) {
            Finality.REVIEW_REQUIRED -> review
            Finality.FINAL -> "(p.status in (2, 3, 4) and not $review)"
            Finality.NON_FINAL -> "(p.status in (0, 1, 5) and not $review)"
        }
    }

    private fun finality(status: PaymentStatus, settlementBlocked: Boolean, blockingReviewCount: Int): Finality = when {
        settlementBlocked || blockingReviewCount > 0 -> Finality.REVIEW_REQUIRED
        status in setOf(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CLOSED) -> Finality.FINAL
        else -> Finality.NON_FINAL
    }

    private fun publicStatus(status: PaymentStatus): String = when (status) {
        PaymentStatus.PENDING -> "PAYABLE"
        else -> status.name
    }

    private fun status(value: Any?): PaymentStatus = PaymentStatus.valueOfOrNull((value as Number).toInt())
        ?: throw IllegalStateException("支付状态投影无效")

    private data class Filter(
        val merchantId: String?,
        val status: String?,
        val finality: Finality?,
        val paymentId: String?,
        val merchantOrderId: String?,
        val channelId: String?,
        val currency: String?,
        val createdFrom: Instant?,
        val createdTo: Instant?,
    ) {
        val fingerprint: String = sha256(
            listOf(merchantId, status, finality?.name, paymentId, merchantOrderId, channelId, currency, createdFrom?.toString(), createdTo?.toString())
                .joinToString("|") { it.orEmpty() },
        )

        companion object {
            fun from(request: ListPaymentIntentsEndpoint.Request): Filter = Filter(
                merchantId = request.merchantId.normalized(),
                status = request.status.normalized()?.uppercase()?.also(::statusToDatabase),
                finality = request.finality,
                paymentId = request.paymentId.normalized(),
                merchantOrderId = request.merchantOrderId.normalized(),
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
        fun statusToDatabase(status: String): PaymentStatus = when (status) {
            "PAYABLE" -> PaymentStatus.PENDING
            else -> runCatching { PaymentStatus.valueOf(status) }.getOrElse {
                throw IllegalArgumentException("不支持的 PaymentIntent status")
            }
        }

        fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
        fun Any?.text(field: String): String = this?.toString()?.takeIf { it.isNotBlank() }
            ?: error("支付缺少 $field")
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
                else -> BigDecimal(value?.toString() ?: error("支付缺少 amount"))
            }
            val minor = amount.movePointRight(DomainMoney.fractionDigits(currency)).toBigIntegerExact().toString()
            return Money(currency = currency, amountMinor = minor)
        }
    }
}
