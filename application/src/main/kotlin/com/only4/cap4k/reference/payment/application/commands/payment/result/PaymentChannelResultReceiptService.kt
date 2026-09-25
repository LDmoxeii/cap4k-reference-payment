package com.only4.cap4k.reference.payment.application.commands.payment.result

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.domain._share.meta.payment_channel_result_receipt.SPaymentChannelResultReceipt
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment_channel_result_receipt.PaymentChannelResultReceipt
import com.only4.cap4k.reference.payment.domain.aggregates.payment_channel_result_receipt.factory.PaymentChannelResultReceiptFactory
import com.only4.cap4k.reference.payment.domain.aggregates.payment_channel_result_receipt.recordReplay
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/**
 * Authoritative transport receipt ledger for payment callbacks.
 *
 * It is deliberately a separate aggregate root: an external result must remain durable and
 * queryable even when no Payment or PaymentAttempt can be associated with it.  A replay of the
 * exact external identity and payload updates only reception metadata; a different payload keeps
 * an independent evidence row and cannot overwrite the first payload.
 */
@Service
class PaymentChannelResultReceiptService {

    fun record(request: RecordRequest): PaymentChannelResultReceipt {
        val normalizedChannel = request.channelId.trim()
        val normalizedResultIdentity = request.resultIdentity.trim()
        val normalizedRawEvidence = request.rawEvidence.trim()
        val payloadIdentity = sha256(normalizedRawEvidence)
        val receivedAt = LocalDateTime.ofInstant(request.receivedAt, ZoneOffset.UTC)
        val existing = Mediator.repositories.findOne(
            SPaymentChannelResultReceipt.predicate { schema ->
                (schema.channelId eq normalizedChannel) and
                    (schema.resultIdentity eq normalizedResultIdentity) and
                    (schema.payloadIdentity eq payloadIdentity)
            },
        )
        if (existing != null) {
            existing.recordReplay(receivedAt)
            return existing
        }
        return Mediator.factories.create<PaymentChannelResultReceiptFactory.Payload, PaymentChannelResultReceipt>(
            PaymentChannelResultReceiptFactory.Payload(
                resultIdentity = normalizedResultIdentity,
                payloadIdentity = payloadIdentity,
                channelId = normalizedChannel,
                paymentId = request.paymentId,
                paymentAttemptId = request.paymentAttemptId?.trim()?.takeIf(String::isNotEmpty),
                channelTransactionId = request.channelTransactionId.trim(),
                rawEvidence = normalizedRawEvidence,
                verification = if (request.verified) "VERIFIED" else "REJECTED",
                verificationSummary = request.verificationSummary?.trim()?.takeIf(String::isNotEmpty),
                outcome = normalizeOutcome(request.outcome),
                amount = request.amount,
                currency = request.currency.trim().uppercase(),
                occurredAt = LocalDateTime.ofInstant(request.occurredAt, ZoneOffset.UTC),
                recordedAt = receivedAt,
                lastReceivedAt = receivedAt,
                disposition = request.disposition.trim().uppercase(),
                rejectionSummary = request.rejectionSummary?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
    }

    data class RecordRequest(
        val channelId: String,
        val resultIdentity: String,
        val rawEvidence: String,
        val paymentId: PaymentId?,
        val paymentAttemptId: String?,
        val channelTransactionId: String,
        val verified: Boolean,
        val verificationSummary: String?,
        val outcome: String,
        val amount: BigDecimal,
        val currency: String,
        val occurredAt: Instant,
        val receivedAt: Instant,
        val disposition: String,
        val rejectionSummary: String?,
    )

    private fun normalizeOutcome(value: String): String = when (value.trim().uppercase()) {
        "FAILED", "FAILURE" -> "FAILURE"
        "RESULT_UNKNOWN", "UNKNOWN" -> "UNKNOWN"
        "SUCCESS" -> "SUCCESS"
        else -> throw IllegalArgumentException("不支持的支付渠道结果：$value")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
