package com.only4.cap4k.reference.payment.domain.aggregates.payment.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.payment.*
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(aggregate = "Payment", name = "PaymentFactory", packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.factory", description = "", type = "factory", root = false)
class PaymentFactory : AggregateFactory<PaymentFactory.Payload, Payment> {
    override fun create(entityPayload: Payload): Payment = Payment(
        merchantId = entityPayload.merchantId,
        merchantOrderNumber = entityPayload.merchantOrderNumber,
        idempotencyKey = entityPayload.idempotencyKey,
        amount = entityPayload.amount,
        currency = entityPayload.currency,
        paymentMethod = entityPayload.paymentMethod,
        status = entityPayload.status,
        expiresAt = entityPayload.expiresAt,
        succeededAt = entityPayload.succeededAt,
        closedAt = entityPayload.closedAt,
        closeReason = entityPayload.closeReason,
        channelTransactionId = entityPayload.channelTransactionId,
        successFactFormed = entityPayload.successFactFormed,
        merchantOrderSuccessIdentity = entityPayload.merchantOrderSuccessIdentity,
        attemptCount = entityPayload.attemptCount,
        notificationReceiveCount = entityPayload.notificationReceiveCount,
        rejectedNotificationCount = entityPayload.rejectedNotificationCount,
        conflictingNotificationCount = entityPayload.conflictingNotificationCount,
        lastNotificationIdentity = entityPayload.lastNotificationIdentity,
        lastNotificationReceivedAt = entityPayload.lastNotificationReceivedAt,
        lastRejectionSummary = entityPayload.lastRejectionSummary,
        lastConflictSummary = entityPayload.lastConflictSummary,
        merchantSuccessNotificationIntentCount = entityPayload.merchantSuccessNotificationIntentCount,
        merchantSuccessNotificationIntentIdentity = entityPayload.merchantSuccessNotificationIntentIdentity,
        merchantSuccessNotificationIntentState = entityPayload.merchantSuccessNotificationIntentState,
        reviewCount = entityPayload.reviewCount,
        blockingReviewCount = entityPayload.blockingReviewCount,
        settlementFeeFactIdentity = entityPayload.settlementFeeFactIdentity,
        settlementFeeBasisPoints = entityPayload.settlementFeeBasisPoints,
        settlementFixedFeeAmount = entityPayload.settlementFixedFeeAmount,
        settlementFeeRoundingMode = entityPayload.settlementFeeRoundingMode,
        settlementFeeCurrencyPrecision = entityPayload.settlementFeeCurrencyPrecision,
        settlementFeeCalculationAmount = entityPayload.settlementFeeCalculationAmount,
        settlementFeeAmount = entityPayload.settlementFeeAmount,
        settlementFeeFormedAt = entityPayload.settlementFeeFormedAt,
        settlementBlocked = entityPayload.settlementBlocked,
        reservedRefundAmount = entityPayload.reservedRefundAmount,
        successfulRefundAmount = entityPayload.successfulRefundAmount,
    ).also { payment ->
        entityPayload.attempts.forEach { payment.attempts.add(createAttempt(it)) }
        entityPayload.reviewCases.forEach { payment.reviewCases.add(createReview(it)) }
    }

    private fun createAttempt(creation: PaymentAttemptCreation): PaymentAttempt = PaymentAttempt(
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
        conflictSummary = creation.conflictSummary,
    ).also { attempt ->
        creation.paymentNotificationReceipts.forEach { attempt.paymentNotificationReceipts.add(createReceipt(it)) }
    }

    private fun createReceipt(c: PaymentNotificationReceiptCreation): PaymentNotificationReceipt =
        PaymentNotificationReceipt(
            notificationIdentity = c.notificationIdentity, payloadIdentity = c.payloadIdentity,
            channelId = c.channelId, channelTransactionId = c.channelTransactionId,
            amount = c.amount, currency = c.currency, result = c.result, occurredAt = c.occurredAt,
            firstReceivedAt = c.firstReceivedAt, lastReceivedAt = c.lastReceivedAt,
            receiveCount = c.receiveCount, verified = c.verified, accepted = c.accepted,
            decision = c.decision, verdictSummary = c.verdictSummary,
            rejectionSummary = c.rejectionSummary, conflictSummary = c.conflictSummary,
        )

    private fun createReview(c: PaymentReviewCaseCreation): PaymentReviewCase = PaymentReviewCase(
        reviewIdentity = c.reviewIdentity, type = c.type, status = c.status, openedAt = c.openedAt,
        triggeringPaymentStatus = c.triggeringPaymentStatus,
        triggeringAttemptIdentities = c.triggeringAttemptIdentities,
        triggeringReceiptIdentities = c.triggeringReceiptIdentities,
        summary = c.summary, settlementImpact = c.settlementImpact, resolvedAt = c.resolvedAt,
    ).also { review ->
        c.paymentReviewDecisions.forEach { review.paymentReviewDecisions.add(createDecision(it)) }
    }

    private fun createDecision(c: PaymentReviewDecisionCreation): PaymentReviewDecision = PaymentReviewDecision(
        decisionIdentity = c.decisionIdentity, decision = c.decision, operatorIdentity = c.operatorIdentity,
        operatorRole = c.operatorRole, authorizationOutcome = c.authorizationOutcome, reason = c.reason,
        evidence = c.evidence, decidedAt = c.decidedAt, eligibilityImpact = c.eligibilityImpact,
        remediationReference = c.remediationReference,
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
        val succeededAt: LocalDateTime? = null,
        val closedAt: LocalDateTime? = null,
        val closeReason: String? = null,
        val channelTransactionId: String? = null,
        val successFactFormed: Boolean = false,
        val merchantOrderSuccessIdentity: String? = null,
        val attemptCount: Int = 0,
        val notificationReceiveCount: Int = 0,
        val rejectedNotificationCount: Int = 0,
        val conflictingNotificationCount: Int = 0,
        val lastNotificationIdentity: String? = null,
        val lastNotificationReceivedAt: LocalDateTime? = null,
        val lastRejectionSummary: String? = null,
        val lastConflictSummary: String? = null,
        val merchantSuccessNotificationIntentCount: Int = 0,
        val merchantSuccessNotificationIntentIdentity: String? = null,
        val merchantSuccessNotificationIntentState: PaymentNotificationIntentState? = null,
        val reviewCount: Int = 0,
        val blockingReviewCount: Int = 0,
        val settlementFeeFactIdentity: String? = null,
        val settlementFeeBasisPoints: Int? = null,
        val settlementFixedFeeAmount: BigDecimal? = null,
        val settlementFeeRoundingMode: String? = null,
        val settlementFeeCurrencyPrecision: Int? = null,
        val settlementFeeCalculationAmount: BigDecimal? = null,
        val settlementFeeAmount: BigDecimal? = null,
        val settlementFeeFormedAt: LocalDateTime? = null,
        val settlementBlocked: Boolean = false,
        val reservedRefundAmount: BigDecimal = BigDecimal.ZERO,
        val successfulRefundAmount: BigDecimal = BigDecimal.ZERO,
        val attempts: List<PaymentAttemptCreation> = emptyList(),
        val reviewCases: List<PaymentReviewCaseCreation> = emptyList(),
    ) : AggregatePayload<Payment>
}
