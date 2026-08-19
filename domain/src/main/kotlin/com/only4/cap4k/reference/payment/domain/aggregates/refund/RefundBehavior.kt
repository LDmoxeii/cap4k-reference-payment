package com.only4.cap4k.reference.payment.domain.aggregates.refund

import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.values.RefundResultRecordingOutcome
import java.math.BigDecimal
import java.time.LocalDateTime

fun Refund.startAttempt(
    now: LocalDateTime,
    channelId: String,
    configurationId: String,
    snapshot: String,
    requestIdentity: String,
    reviewAfterAt: LocalDateTime,
): RefundAttempt {
    require(status == RefundStatus.PROCESSING || status == RefundStatus.RESULT_UNKNOWN || status == RefundStatus.REVIEW_REQUIRED) {
        "refund $id cannot start while $status"
    }
    attempts.firstOrNull { it.status == RefundAttemptStatus.PROCESSING }?.let { return it }
    val attempt = RefundAttempt(
        channelId = channelId,
        channelConfigurationId = configurationId,
        channelConfigurationSnapshot = snapshot,
        requestIdentity = requestIdentity,
        status = RefundAttemptStatus.PROCESSING,
        initiatedAt = now,
        reviewAfterAt = reviewAfterAt,
    )
    attempts.add(attempt)
    this.channelId = channelId
    this.channelConfigurationId = configurationId
    this.channelConfigurationSnapshot = snapshot
    this.requestIdentity = requestIdentity
    status = RefundStatus.PROCESSING
    return attempt
}

fun Refund.markChannelAccepted(
    attemptId: RefundAttemptId,
    channelRefundId: String,
    acceptedAt: LocalDateTime,
) {
    val attempt = attempts.firstOrNull { it.id == attemptId }
        ?: error("refund attempt $attemptId does not belong to refund $id")
    require(attempt.status == RefundAttemptStatus.PROCESSING) {
        "refund attempt $attemptId cannot be accepted while ${attempt.status}"
    }
    attempt.acceptedAt = acceptedAt
    attempt.channelRefundId = channelRefundId
    this.channelRefundId = channelRefundId
    channelAcceptedAt = acceptedAt
}

fun Refund.rejectAttemptStart(
    attemptId: RefundAttemptId,
    failureCode: String,
    diagnosticSummary: String?,
) {
    val attempt = attempts.firstOrNull { it.id == attemptId }
        ?: error("refund attempt $attemptId does not belong to refund $id")
    attempt.status = RefundAttemptStatus.FAILED
    attempt.finalResult = RefundAttemptFinalResult.GATEWAY_REJECTED
    attempt.rejectionSummary = listOfNotNull(failureCode, diagnosticSummary).joinToString(": ")
    status = RefundStatus.FAILED
    reservationActive = false
    reservationReleased = true
    finalizedAt = attempt.initiatedAt
    lastRejectionSummary = attempt.rejectionSummary
    rejectedNotificationCount += 1
}

fun Refund.recordChannelResult(
    attemptId: RefundAttemptId,
    channelId: String,
    notificationId: String,
    channelRefundId: String,
    amount: BigDecimal,
    currency: String,
    result: String,
    occurredAt: LocalDateTime,
    receivedAt: LocalDateTime,
    verified: Boolean,
    verificationSummary: String?,
): RefundResultRecordingOutcome {
    val normalizedResult = result.trim().uppercase()
    require(normalizedResult in setOf("SUCCESS", "FAILED", "UNKNOWN")) {
        "unsupported refund channel result: $result"
    }
    val normalizedCurrency = currency.trim().uppercase()
    notificationReceiveCount += 1
    lastNotificationIdentity = notificationId
    lastNotificationReceivedAt = receivedAt

    val attempt = attempts.firstOrNull { it.id == attemptId }
        ?: run {
            val rejection = "refund attempt $attemptId does not belong to refund $id"
            rejectedNotificationCount += 1
            lastRejectionSummary = rejection
            return outcome(null, RefundResultDisposition.ATTEMPT_NOT_FOUND, rejection)
        }
    attempt.notificationReceiveCount += 1
    attempt.notificationFirstReceivedAt = attempt.notificationFirstReceivedAt ?: receivedAt
    attempt.notificationLastReceivedAt = receivedAt

    val existing = attempt.refundNotificationReceipts.firstOrNull { it.notificationIdentity == notificationId }
    if (existing != null) {
        existing.receiveCount += 1
        existing.lastReceivedAt = receivedAt
        if (!existing.samePayload(channelId, channelRefundId, amount, normalizedCurrency, normalizedResult, occurredAt)) {
            return markConflict(attempt, existing, "notification $notificationId was reused with a conflicting payload")
        }
        val disposition = when {
            existing.decision == RefundResultDisposition.CONFLICT -> RefundResultDisposition.CONFLICT
            existing.accepted -> RefundResultDisposition.ACCEPTED_DUPLICATE
            else -> RefundResultDisposition.REJECTED_DUPLICATE
        }
        return outcome(attempt, disposition, existing.rejectionSummary, existing.conflictSummary)
    }

    val receipt = RefundNotificationReceipt(
        notificationIdentity = notificationId,
        channelId = channelId,
        channelRefundId = channelRefundId,
        amount = amount,
        currency = normalizedCurrency,
        result = normalizedResult,
        occurredAt = occurredAt,
        firstReceivedAt = receivedAt,
        lastReceivedAt = receivedAt,
        verified = verified,
        accepted = false,
        decision = RefundResultDisposition.RECEIVED,
        verdictSummary = verificationSummary,
    )
    attempt.refundNotificationReceipts.add(receipt)

    if (attempt.finalResult != null || status == RefundStatus.SUCCEEDED) {
        return markConflict(attempt, receipt, "notification $notificationId conflicts with finalized refund attempt ${attempt.id}")
    }

    val rejection = when {
        !verified -> verificationSummary ?: "channel verification failed"
        attempt.channelId != channelId -> "channel $channelId does not match attempt channel ${attempt.channelId}"
        this.amount.compareTo(amount) != 0 -> "notification amount $amount does not match refund amount ${this.amount}"
        this.currency != normalizedCurrency -> "notification currency $normalizedCurrency does not match refund currency ${this.currency}"
        attempt.channelRefundId != null && attempt.channelRefundId != channelRefundId -> "channel refund id does not match the accepted attempt"
        else -> null
    }
    if (rejection != null) {
        receipt.decision = RefundResultDisposition.REJECTED
        receipt.rejectionSummary = rejection
        rejectedNotificationCount += 1
        lastRejectionSummary = rejection
        attempt.rejectedNotificationCount += 1
        attempt.rejectionSummary = rejection
        return outcome(attempt, RefundResultDisposition.REJECTED, rejectionSummary = rejection)
    }

    receipt.accepted = true
    attempt.verifiedNotificationCount += 1
    attempt.verdictSummary = verificationSummary ?: "verified"
    attempt.channelRefundId = channelRefundId
    this.channelRefundId = channelRefundId
    attempt.resultOccurredAt = occurredAt

    return when (normalizedResult) {
        "SUCCESS" -> {
            receipt.decision = RefundResultDisposition.SUCCESS_ACCEPTED
            attempt.status = RefundAttemptStatus.SUCCEEDED
            attempt.finalResult = RefundAttemptFinalResult.SUCCESS
            status = RefundStatus.SUCCEEDED
            finalizedAt = occurredAt
            reservationActive = false
            val convertedNow = !reservationConvertedToSuccess
            reservationConvertedToSuccess = true
            successFactFormed = true
            outcome(attempt, RefundResultDisposition.SUCCESS_ACCEPTED, reservationConvertedToSuccessNow = convertedNow)
        }
        "FAILED" -> {
            receipt.decision = RefundResultDisposition.FAILURE_ACCEPTED
            attempt.status = RefundAttemptStatus.FAILED
            attempt.finalResult = RefundAttemptFinalResult.FAILED
            status = RefundStatus.FAILED
            finalizedAt = occurredAt
            reservationActive = false
            val releasedNow = !reservationReleased
            reservationReleased = true
            outcome(attempt, RefundResultDisposition.FAILURE_ACCEPTED, reservationReleasedNow = releasedNow)
        }
        else -> {
            receipt.decision = RefundResultDisposition.UNKNOWN_ACCEPTED
            attempt.status = RefundAttemptStatus.RESULT_UNKNOWN
            status = RefundStatus.RESULT_UNKNOWN
            outcome(attempt, RefundResultDisposition.UNKNOWN_ACCEPTED)
        }
    }
}

fun Refund.markReviewRequired(now: LocalDateTime): Boolean {
    var changed = false
    attempts.filter {
        it.status in setOf(RefundAttemptStatus.PROCESSING, RefundAttemptStatus.RESULT_UNKNOWN) &&
            it.reviewAfterAt <= now
    }.forEach {
        it.status = RefundAttemptStatus.REVIEW_REQUIRED
        changed = true
    }
    if (changed) {
        status = RefundStatus.REVIEW_REQUIRED
        reviewRequiredAt = now
    }
    return changed
}

private fun Refund.markConflict(
    attempt: RefundAttempt,
    receipt: RefundNotificationReceipt,
    summary: String,
): RefundResultRecordingOutcome {
    receipt.accepted = false
    receipt.decision = RefundResultDisposition.CONFLICT
    receipt.conflictSummary = summary
    attempt.conflictingNotificationCount += 1
    attempt.conflictSummary = summary
    conflictingNotificationCount += 1
    lastConflictSummary = summary
    settlementBlocked = true
    return outcome(attempt, RefundResultDisposition.CONFLICT, conflictSummary = summary)
}

private fun RefundNotificationReceipt.samePayload(
    channelId: String,
    channelRefundId: String,
    amount: BigDecimal,
    currency: String,
    result: String,
    occurredAt: LocalDateTime,
): Boolean =
    this.channelId == channelId &&
        this.channelRefundId == channelRefundId &&
        this.amount.compareTo(amount) == 0 &&
        this.currency == currency &&
        this.result == result &&
        this.occurredAt == occurredAt

private fun Refund.outcome(
    attempt: RefundAttempt?,
    disposition: RefundResultDisposition,
    rejectionSummary: String? = null,
    conflictSummary: String? = null,
    reservationReleasedNow: Boolean = false,
    reservationConvertedToSuccessNow: Boolean = false,
): RefundResultRecordingOutcome = RefundResultRecordingOutcome(
    refundStatus = status,
    attemptStatus = attempt?.status,
    notificationReceiveCount = notificationReceiveCount,
    disposition = disposition,
    reservationReleasedNow = reservationReleasedNow,
    reservationConvertedToSuccessNow = reservationConvertedToSuccessNow,
    reviewRequiredNow = status == RefundStatus.REVIEW_REQUIRED,
    rejectionSummary = rejectionSummary,
    conflictSummary = conflictSummary,
)
