package com.only4.cap4k.reference.payment.domain.aggregates.refund.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttemptCreation
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundNotificationReceipt
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundNotificationReceiptCreation
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "Refund",
    name = "RefundFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.factory",
    description = "",
    type = "factory",
    root = false
)
class RefundFactory : AggregateFactory<RefundFactory.Payload, Refund> {

    override fun create(entityPayload: Payload): Refund =
        Refund(
            paymentId = entityPayload.paymentId,
            merchantId = entityPayload.merchantId,
            merchantRefundNumber = entityPayload.merchantRefundNumber,
            amount = entityPayload.amount,
            currency = entityPayload.currency,
            paymentMethod = entityPayload.paymentMethod,
            status = entityPayload.status,
            requestedAt = entityPayload.requestedAt,
            refundDeadlineAt = entityPayload.refundDeadlineAt,
            channelAcceptedAt = entityPayload.channelAcceptedAt,
            finalizedAt = entityPayload.finalizedAt,
            reviewRequiredAt = entityPayload.reviewRequiredAt,
            channelId = entityPayload.channelId,
            channelConfigurationId = entityPayload.channelConfigurationId,
            channelConfigurationSnapshot = entityPayload.channelConfigurationSnapshot,
            requestIdentity = entityPayload.requestIdentity,
            channelRefundId = entityPayload.channelRefundId,
            reservationActive = entityPayload.reservationActive,
            reservationReleased = entityPayload.reservationReleased,
            reservationConvertedToSuccess = entityPayload.reservationConvertedToSuccess,
            successFactFormed = entityPayload.successFactFormed,
            notificationReceiveCount = entityPayload.notificationReceiveCount,
            rejectedNotificationCount = entityPayload.rejectedNotificationCount,
            conflictingNotificationCount = entityPayload.conflictingNotificationCount,
            lastNotificationIdentity = entityPayload.lastNotificationIdentity,
            lastNotificationReceivedAt = entityPayload.lastNotificationReceivedAt,
            lastRejectionSummary = entityPayload.lastRejectionSummary,
            lastConflictSummary = entityPayload.lastConflictSummary,
            settlementBlocked = entityPayload.settlementBlocked
        ).also { aggregate ->
            entityPayload.attempts.forEach { childCreation ->
                aggregate.attempts.add(createRefundAttempt(childCreation))
            }
        }

    private fun createRefundNotificationReceipt(creation: RefundNotificationReceiptCreation): RefundNotificationReceipt =
        RefundNotificationReceipt(
            notificationIdentity = creation.notificationIdentity,
            channelId = creation.channelId,
            channelRefundId = creation.channelRefundId,
            amount = creation.amount,
            currency = creation.currency,
            result = creation.result,
            occurredAt = creation.occurredAt,
            firstReceivedAt = creation.firstReceivedAt,
            lastReceivedAt = creation.lastReceivedAt,
            receiveCount = creation.receiveCount,
            verified = creation.verified,
            accepted = creation.accepted,
            decision = creation.decision,
            verdictSummary = creation.verdictSummary,
            rejectionSummary = creation.rejectionSummary,
            conflictSummary = creation.conflictSummary
        )

    private fun createRefundAttempt(creation: RefundAttemptCreation): RefundAttempt =
        RefundAttempt(
            channelId = creation.channelId,
            channelConfigurationId = creation.channelConfigurationId,
            channelConfigurationSnapshot = creation.channelConfigurationSnapshot,
            requestIdentity = creation.requestIdentity,
            status = creation.status,
            initiatedAt = creation.initiatedAt,
            acceptedAt = creation.acceptedAt,
            reviewAfterAt = creation.reviewAfterAt,
            channelRefundId = creation.channelRefundId,
            finalResult = creation.finalResult,
            resultOccurredAt = creation.resultOccurredAt,
            notificationReceiveCount = creation.notificationReceiveCount,
            notificationFirstReceivedAt = creation.notificationFirstReceivedAt,
            notificationLastReceivedAt = creation.notificationLastReceivedAt,
            verifiedNotificationCount = creation.verifiedNotificationCount,
            rejectedNotificationCount = creation.rejectedNotificationCount,
            conflictingNotificationCount = creation.conflictingNotificationCount,
            verdictSummary = creation.verdictSummary,
            rejectionSummary = creation.rejectionSummary,
            conflictSummary = creation.conflictSummary
        ).also { entity ->
            creation.refundNotificationReceipts.forEach { childCreation ->
                entity.refundNotificationReceipts.add(createRefundNotificationReceipt(childCreation))
            }
        }

    data class Payload(
        val paymentId: PaymentId,
        val merchantId: String,
        val merchantRefundNumber: String,
        val amount: BigDecimal,
        val currency: String,
        val paymentMethod: String,
        val status: RefundStatus,
        val requestedAt: LocalDateTime,
        val refundDeadlineAt: LocalDateTime,
        val channelAcceptedAt: LocalDateTime?,
        val finalizedAt: LocalDateTime?,
        val reviewRequiredAt: LocalDateTime?,
        val channelId: String,
        val channelConfigurationId: String,
        val channelConfigurationSnapshot: String,
        val requestIdentity: String,
        val channelRefundId: String?,
        val reservationActive: Boolean = true,
        val reservationReleased: Boolean = false,
        val reservationConvertedToSuccess: Boolean = false,
        val successFactFormed: Boolean = false,
        val notificationReceiveCount: Int = 0,
        val rejectedNotificationCount: Int = 0,
        val conflictingNotificationCount: Int = 0,
        val lastNotificationIdentity: String?,
        val lastNotificationReceivedAt: LocalDateTime?,
        val lastRejectionSummary: String?,
        val lastConflictSummary: String?,
        val settlementBlocked: Boolean = false,
        val attempts: List<RefundAttemptCreation> = emptyList()
    ) : AggregatePayload<Refund>
}
