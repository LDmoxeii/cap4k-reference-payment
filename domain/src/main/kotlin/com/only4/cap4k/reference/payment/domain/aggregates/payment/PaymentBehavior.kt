package com.only4.cap4k.reference.payment.domain.aggregates.payment

import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import java.math.BigDecimal
import java.time.LocalDateTime

fun Payment.startAttempt(
    channelId: String,
    channelConfigurationId: String,
    channelConfigurationSnapshot: String,
    requestIdentity: String,
    initiatedAt: LocalDateTime,
): PaymentAttempt {
    check(status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING) {
        "payment $id cannot start an attempt while status is $status"
    }

    attempts.firstOrNull { it.status == PaymentAttemptStatus.PROCESSING }?.let { return it }

    val attempt = PaymentAttempt(
        channelId = channelId,
        channelConfigurationId = channelConfigurationId,
        channelConfigurationSnapshot = channelConfigurationSnapshot,
        requestIdentity = requestIdentity,
        status = PaymentAttemptStatus.PROCESSING,
        initiatedAt = initiatedAt,
    )
    attempts.add(attempt)
    attemptCount = attempts.size
    status = PaymentStatus.PROCESSING
    return attempt
}

fun Payment.rejectAttemptStart(
    paymentAttemptId: PaymentAttemptId,
    failureCode: String,
    diagnosticSummary: String?,
) {
    val attempt = attempts.firstOrNull { it.id == paymentAttemptId }
        ?: error("payment attempt $paymentAttemptId does not belong to payment $id")
    attempt.status = PaymentAttemptStatus.FAILED
    attempt.finalResult = PaymentAttemptFinalResult.GATEWAY_REJECTED
    attempt.rejectionSummary = listOfNotNull(failureCode, diagnosticSummary).joinToString(": ")
    rejectedNotificationCount += 1
    lastRejectionSummary = attempt.rejectionSummary
    status = PaymentStatus.FAILED
}

fun Payment.recordChannelResult(
    paymentAttemptId: PaymentAttemptId,
    channelId: String,
    notificationId: String,
    channelTransactionId: String,
    amount: BigDecimal,
    currency: String,
    result: String,
    occurredAt: LocalDateTime,
    receivedAt: LocalDateTime,
    verified: Boolean,
    verificationSummary: String?,
): ChannelResultRecordingOutcome {
    val normalizedResult = result.trim().uppercase()
    require(normalizedResult == "SUCCESS" || normalizedResult == "FAILED") {
        "unsupported channel result: $result"
    }

    notificationReceiveCount += 1
    lastNotificationIdentity = notificationId
    lastNotificationReceivedAt = receivedAt

    val attempt = attempts.firstOrNull { it.id == paymentAttemptId }
    if (attempt == null) {
        val summary = "payment attempt $paymentAttemptId does not belong to payment $id"
        rejectedNotificationCount += 1
        lastRejectionSummary = summary
        return ChannelResultRecordingOutcome(
            paymentStatus = status,
            attemptStatus = null,
            notificationReceiveCount = notificationReceiveCount,
            disposition = ChannelResultDisposition.ATTEMPT_NOT_FOUND,
            rejectionSummary = summary,
            conflictSummary = null,
            successFactFormedNow = false,
        )
    }

    attempt.notificationReceiveCount += 1
    attempt.notificationFirstReceivedAt = attempt.notificationFirstReceivedAt ?: receivedAt
    attempt.notificationLastReceivedAt = receivedAt

    val existingReceipt = attempt.paymentNotificationReceipts.firstOrNull {
        it.notificationIdentity == notificationId
    }
    if (existingReceipt != null) {
        existingReceipt.receiveCount += 1
        existingReceipt.lastReceivedAt = receivedAt
        if (!existingReceipt.samePayload(channelId, channelTransactionId, amount, currency, normalizedResult, occurredAt)) {
            val summary = "notification $notificationId reused with conflicting payload"
            existingReceipt.accepted = false
            existingReceipt.decision = ChannelResultDisposition.CONFLICT
            existingReceipt.conflictSummary = summary
            attempt.conflictingNotificationCount += 1
            attempt.conflictSummary = summary
            conflictingNotificationCount += 1
            lastConflictSummary = summary
            settlementBlocked = true
            return outcome(
                attempt = attempt,
                disposition = ChannelResultDisposition.CONFLICT,
                conflictSummary = summary,
            )
        }

        val duplicateDisposition = when {
            existingReceipt.accepted -> ChannelResultDisposition.ACCEPTED_DUPLICATE
            existingReceipt.decision == ChannelResultDisposition.CONFLICT -> ChannelResultDisposition.CONFLICT
            else -> ChannelResultDisposition.REJECTED_DUPLICATE
        }
        existingReceipt.decision = duplicateDisposition
        return outcome(
            attempt = attempt,
            disposition = duplicateDisposition,
            rejectionSummary = existingReceipt.rejectionSummary,
            conflictSummary = existingReceipt.conflictSummary,
        )
    }

    val receipt = PaymentNotificationReceipt(
        notificationIdentity = notificationId,
        channelId = channelId,
        channelTransactionId = channelTransactionId,
        amount = amount,
        currency = currency,
        result = normalizedResult,
        occurredAt = occurredAt,
        firstReceivedAt = receivedAt,
        lastReceivedAt = receivedAt,
        verified = verified,
        accepted = false,
        decision = ChannelResultDisposition.RECEIVED,
        verdictSummary = verificationSummary,
    )
    attempt.paymentNotificationReceipts.add(receipt)

    if (attempt.finalResult != null) {
        val summary = "notification $notificationId conflicts with finalized attempt ${attempt.id}"
        receipt.decision = ChannelResultDisposition.CONFLICT
        receipt.conflictSummary = summary
        conflictingNotificationCount += 1
        lastConflictSummary = summary
        settlementBlocked = true
        attempt.conflictingNotificationCount += 1
        attempt.conflictSummary = summary
        return outcome(
            attempt = attempt,
            disposition = ChannelResultDisposition.CONFLICT,
            conflictSummary = summary,
        )
    }

    val rejection = when {
        !verified -> verificationSummary ?: "channel verification failed"
        attempt.channelId != channelId -> "channel $channelId does not match attempt channel ${attempt.channelId}"
        this.amount.compareTo(amount) != 0 -> "notification amount $amount does not match payment amount ${this.amount}"
        this.currency != currency -> "notification currency $currency does not match payment currency ${this.currency}"
        else -> null
    }
    if (rejection != null) {
        receipt.decision = ChannelResultDisposition.REJECTED
        receipt.rejectionSummary = rejection
        rejectedNotificationCount += 1
        lastRejectionSummary = rejection
        attempt.rejectedNotificationCount += 1
        attempt.rejectionSummary = rejection
        attempt.verdictSummary = verificationSummary
        return outcome(
            attempt = attempt,
            disposition = ChannelResultDisposition.REJECTED,
            rejectionSummary = rejection,
        )
    }

    attempt.notificationIdentity = notificationId
    attempt.verifiedNotificationCount += 1
    attempt.verdictSummary = verificationSummary ?: "verified"
    attempt.channelTransactionId = channelTransactionId
    attempt.resultOccurredAt = occurredAt
    receipt.accepted = true

    return if (normalizedResult == "SUCCESS") {
        receipt.decision = ChannelResultDisposition.SUCCESS_ACCEPTED
        attempt.finalResult = PaymentAttemptFinalResult.SUCCESS
        attempt.status = PaymentAttemptStatus.SUCCEEDED
        status = PaymentStatus.SUCCEEDED
        succeededAt = occurredAt
        this.channelTransactionId = channelTransactionId
        val formedNow = !successFactFormed
        successFactFormed = true
        if (formedNow) {
            merchantSuccessNotificationIntentCount += 1
        }
        outcome(
            attempt = attempt,
            disposition = ChannelResultDisposition.SUCCESS_ACCEPTED,
            successFactFormedNow = formedNow,
        )
    } else {
        receipt.decision = ChannelResultDisposition.FAILURE_ACCEPTED
        attempt.finalResult = PaymentAttemptFinalResult.FAILED
        attempt.status = PaymentAttemptStatus.FAILED
        status = PaymentStatus.FAILED
        outcome(
            attempt = attempt,
            disposition = ChannelResultDisposition.FAILURE_ACCEPTED,
        )
    }
}

private fun PaymentNotificationReceipt.samePayload(
    channelId: String,
    channelTransactionId: String,
    amount: BigDecimal,
    currency: String,
    result: String,
    occurredAt: LocalDateTime,
): Boolean =
    this.channelId == channelId &&
        this.channelTransactionId == channelTransactionId &&
        this.amount.compareTo(amount) == 0 &&
        this.currency == currency &&
        this.result == result &&
        this.occurredAt == occurredAt

private fun Payment.outcome(
    attempt: PaymentAttempt,
    disposition: ChannelResultDisposition,
    rejectionSummary: String? = null,
    conflictSummary: String? = null,
    successFactFormedNow: Boolean = false,
): ChannelResultRecordingOutcome = ChannelResultRecordingOutcome(
    paymentStatus = status,
    attemptStatus = attempt.status,
    notificationReceiveCount = notificationReceiveCount,
    disposition = disposition,
    rejectionSummary = rejectionSummary,
    conflictSummary = conflictSummary,
    successFactFormedNow = successFactFormedNow,
)

fun Payment.onCreate() = Unit

fun Payment.onDeleted() = Unit

val Payment.refundableAmount: BigDecimal
    get() = amount.subtract(reservedRefundAmount).subtract(successfulRefundAmount)

fun Payment.reserveRefund(amount: BigDecimal) {
    require(status == PaymentStatus.SUCCEEDED) { "payment $id is not successful" }
    require(amount > BigDecimal.ZERO) { "refund amount must be positive" }
    if (refundableAmount < amount) {
        throw RefundBudgetConflictException("payment $id has only $refundableAmount refundable amount")
    }
    reservedRefundAmount = reservedRefundAmount.add(amount)
}

fun Payment.releaseRefundReservation(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO) { "refund amount must be positive" }
    require(reservedRefundAmount >= amount) { "payment $id has no matching refund reservation" }
    reservedRefundAmount = reservedRefundAmount.subtract(amount)
}

fun Payment.convertRefundReservationToSuccess(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO) { "refund amount must be positive" }
    require(reservedRefundAmount >= amount) { "payment $id has no matching refund reservation" }
    reservedRefundAmount = reservedRefundAmount.subtract(amount)
    successfulRefundAmount = successfulRefundAmount.add(amount)
}
