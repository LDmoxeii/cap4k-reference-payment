package com.only4.cap4k.reference.payment.adapter.application.queries.payment.read

import com.only4.cap4k.reference.payment.contract.common.ChannelResultDisposition
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentChannelResultReceiptsEndpoint
import com.only4.cap4k.reference.payment.domain.values.Money as DomainMoney
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/** Bounded authoritative query over the transport receipt aggregate, including unassociated results. */
@Service
class PaymentChannelResultReceiptReadModel(private val entityManager: EntityManager) {
    fun get(
        request: GetPaymentChannelResultReceiptsEndpoint.Request,
    ): GetPaymentChannelResultReceiptsEndpoint.Response {
        val identity = request.resultIdentity.trim().also { require(it.isNotEmpty()) { "resultIdentity 不能为空" } }
        val channel = request.channelId?.trim()?.takeIf(String::isNotEmpty)
        val sql = buildString {
            append("select id, result_identity, payload_identity, channel_id, payment_id, payment_attempt_id, ")
            append("channel_transaction_id, raw_evidence, verification, verification_summary, outcome, amount, currency, ")
            append("occurred_at, recorded_at, last_received_at, receive_count, disposition, rejection_summary ")
            append("from payment_channel_result_receipt where result_identity = ?1 ")
            if (channel != null) append("and channel_id = ?2 ")
            append("order by recorded_at asc, id asc")
        }
        val query = entityManager.createNativeQuery(sql).setParameter(1, identity)
        if (channel != null) query.setParameter(2, channel)
        val receipts = query.resultList.map { row -> toReceipt(row as Array<Any?>) }
        require(receipts.isNotEmpty()) { "未找到支付渠道结果收件：$identity" }
        return GetPaymentChannelResultReceiptsEndpoint.Response(
            resultIdentity = identity,
            totalReceiveCount = receipts.sumOf { it.receiveCount },
            receipts = receipts,
        )
    }

    private fun toReceipt(row: Array<Any?>): GetPaymentChannelResultReceiptsEndpoint.Response.Receipt {
        val currency = row[12].text("currency").uppercase()
        val amount = row[11].decimal("amount")
        return GetPaymentChannelResultReceiptsEndpoint.Response.Receipt(
            resultReceiptId = row[0].text("resultReceiptId"),
            resultIdentity = row[1].text("resultIdentity"),
            payloadIdentity = row[2].text("payloadIdentity"),
            channelId = row[3].text("channelId"),
            paymentId = row[4]?.toString(),
            paymentAttemptId = row[5]?.toString(),
            channelTransactionId = row[6].text("channelTransactionId"),
            rawEvidence = row[7].text("rawEvidence"),
            verification = row[8].text("verification"),
            verificationSummary = row[9]?.toString(),
            outcome = row[10].text("outcome"),
            money = Money(
                currency = currency,
                amountMinor = amount.movePointRight(DomainMoney.fractionDigits(currency)).toBigIntegerExact().toString(),
            ),
            occurredAt = row[13].instant("occurredAt"),
            recordedAt = row[14].instant("recordedAt"),
            lastReceivedAt = row[15].instant("lastReceivedAt"),
            receiveCount = (row[16] as Number).toInt(),
            disposition = ChannelResultDisposition.valueOf(row[17].text("disposition")),
            rejectionSummary = row[18]?.toString(),
        )
    }

    private fun Any?.text(field: String): String = this?.toString()?.takeIf(String::isNotBlank)
        ?: error("支付渠道结果收件缺少 $field")

    private fun Any?.decimal(field: String): BigDecimal = when (this) {
        is BigDecimal -> this
        is Number -> BigDecimal(toString())
        null -> error("支付渠道结果收件缺少 $field")
        else -> BigDecimal(toString())
    }

    private fun Any?.instant(field: String): Instant = when (this) {
        is Instant -> this
        is OffsetDateTime -> toInstant()
        is LocalDateTime -> toInstant(ZoneOffset.UTC)
        is Timestamp -> toInstant()
        is java.util.Date -> toInstant()
        null -> error("支付渠道结果收件缺少 $field")
        else -> Instant.parse(toString())
    }
}
