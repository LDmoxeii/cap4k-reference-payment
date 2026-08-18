package com.only4.cap4k.reference.payment.domain.aggregates.payment.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptCreation
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "Payment",
    name = "PaymentFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.factory",
    description = "",
    type = "factory",
    root = false
)
class PaymentFactory : AggregateFactory<PaymentFactory.Payload, Payment> {

    override fun create(entityPayload: Payload): Payment =
        Payment(
            merchantId = entityPayload.merchantId,
            merchantOrderNumber = entityPayload.merchantOrderNumber,
            idempotencyKey = entityPayload.idempotencyKey,
            amount = entityPayload.amount,
            currency = entityPayload.currency,
            paymentMethod = entityPayload.paymentMethod,
            status = entityPayload.status,
            expiresAt = entityPayload.expiresAt,
            succeededAt = entityPayload.succeededAt,
            channelTransactionId = entityPayload.channelTransactionId,
            successFactFormed = entityPayload.successFactFormed,
            attemptCount = entityPayload.attemptCount,
            notificationReceiveCount = entityPayload.notificationReceiveCount,
            rejectedNotificationCount = entityPayload.rejectedNotificationCount,
            conflictingNotificationCount = entityPayload.conflictingNotificationCount,
            lastRejectionSummary = entityPayload.lastRejectionSummary,
            lastConflictSummary = entityPayload.lastConflictSummary,
            settlementBlocked = entityPayload.settlementBlocked
        ).also { aggregate ->
            entityPayload.attempts.forEach { childCreation ->
                aggregate.attempts.add(createPaymentAttempt(childCreation))
            }
        }

    private fun createPaymentAttempt(creation: PaymentAttemptCreation): PaymentAttempt =
        PaymentAttempt(
            channelId = creation.channelId,
            channelConfigurationId = creation.channelConfigurationId,
            channelConfigurationSnapshot = creation.channelConfigurationSnapshot,
            requestIdentity = creation.requestIdentity,
            status = creation.status,
            initiatedAt = creation.initiatedAt,
            channelTransactionId = creation.channelTransactionId,
            finalResult = creation.finalResult,
            resultOccurredAt = creation.resultOccurredAt,
            notificationIdentity = creation.notificationIdentity,
            notificationReceiveCount = creation.notificationReceiveCount,
            notificationFirstReceivedAt = creation.notificationFirstReceivedAt,
            notificationLastReceivedAt = creation.notificationLastReceivedAt,
            verifiedNotificationCount = creation.verifiedNotificationCount,
            rejectedNotificationCount = creation.rejectedNotificationCount,
            conflictingNotificationCount = creation.conflictingNotificationCount,
            verdictSummary = creation.verdictSummary,
            rejectionSummary = creation.rejectionSummary,
            conflictSummary = creation.conflictSummary
        )

    data class Payload(
        val merchantId: String,
        val merchantOrderNumber: String,
        val idempotencyKey: String,
        val amount: BigDecimal,
        val currency: String,
        val paymentMethod: String,
        val status: PaymentStatus,
        val expiresAt: LocalDateTime,
        val succeededAt: LocalDateTime?,
        val channelTransactionId: String?,
        val successFactFormed: Boolean = false,
        val attemptCount: Int = 0,
        val notificationReceiveCount: Int = 0,
        val rejectedNotificationCount: Int = 0,
        val conflictingNotificationCount: Int = 0,
        val lastRejectionSummary: String?,
        val lastConflictSummary: String?,
        val settlementBlocked: Boolean = false,
        val attempts: List<PaymentAttemptCreation> = emptyList()
    ) : AggregatePayload<Payment>
}
