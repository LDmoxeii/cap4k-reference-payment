package com.only4.cap4k.reference.payment.domain.aggregates.payment_channel_result_receipt.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment_channel_result_receipt.PaymentChannelResultReceipt
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "PaymentChannelResultReceipt",
    name = "PaymentChannelResultReceiptFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment_channel_result_receipt.factory",
    description = "支付渠道结果权威收件：独立于目标聚合保存全部外部结果，使未知 payment/attempt 引用仍可按 external identity 查询",
    type = "factory",
    root = false
)
class PaymentChannelResultReceiptFactory : AggregateFactory<PaymentChannelResultReceiptFactory.Payload, PaymentChannelResultReceipt> {

    override fun create(entityPayload: Payload): PaymentChannelResultReceipt =
        PaymentChannelResultReceipt(
            resultIdentity = entityPayload.resultIdentity,
            payloadIdentity = entityPayload.payloadIdentity,
            channelId = entityPayload.channelId,
            paymentId = entityPayload.paymentId,
            paymentAttemptId = entityPayload.paymentAttemptId,
            channelTransactionId = entityPayload.channelTransactionId,
            rawEvidence = entityPayload.rawEvidence,
            verification = entityPayload.verification,
            verificationSummary = entityPayload.verificationSummary,
            outcome = entityPayload.outcome,
            amount = entityPayload.amount,
            currency = entityPayload.currency,
            occurredAt = entityPayload.occurredAt,
            recordedAt = entityPayload.recordedAt,
            lastReceivedAt = entityPayload.lastReceivedAt,
            receiveCount = entityPayload.receiveCount,
            disposition = entityPayload.disposition,
            rejectionSummary = entityPayload.rejectionSummary
        )

    data class Payload(
        val resultIdentity: String,
        val payloadIdentity: String,
        val channelId: String,
        val paymentId: PaymentId?,
        val paymentAttemptId: String?,
        val channelTransactionId: String,
        val rawEvidence: String,
        val verification: String,
        val verificationSummary: String?,
        val outcome: String,
        val amount: BigDecimal,
        val currency: String,
        val occurredAt: LocalDateTime,
        val recordedAt: LocalDateTime,
        val lastReceivedAt: LocalDateTime,
        val receiveCount: Int = 1,
        val disposition: String,
        val rejectionSummary: String?
    ) : AggregatePayload<PaymentChannelResultReceipt>
}
