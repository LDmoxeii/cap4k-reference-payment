package com.only4.cap4k.reference.payment.domain.aggregates.payment

import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.*
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.PaymentExpiryOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.PaymentReviewEligibility
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime

data class SettlementFeeRule(
    val configurationId: String,
    val basisPoints: Int,
    val fixedFeeAmount: BigDecimal,
    val roundingMode: RoundingMode,
    val currencyPrecision: Int,
) {
    init {
        require(configurationId.isNotBlank())
        require(basisPoints >= 0)
        require(fixedFeeAmount >= BigDecimal.ZERO)
        require(currencyPrecision >= 0)
    }
}

data class PaymentReviewAdjudicationOutcome(
    val paymentStatus: PaymentStatus,
    val reviewStatus: PaymentReviewStatus,
    val decisionCount: Int,
    val settlementEligible: Boolean,
    val notificationIntentState: PaymentNotificationIntentState?,
)

fun Payment.startAttempt(
    channelId: String,
    channelConfigurationId: String,
    channelConfigurationSnapshot: String,
    requestIdentity: String,
    initiatedAt: LocalDateTime,
): PaymentAttempt {
    check(initiatedAt.isBefore(expiresAt)) { "PAYMENT_EXPIRED" }
    check(currentReviewEligibility().settlementEligible) { "PAYMENT_REVIEW_REQUIRED" }
    check(status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING) {
        "payment $id cannot start an attempt while status is $status"
    }
    attempts.firstOrNull { it.status == PaymentAttemptStatus.PROCESSING }?.let { return it }
    return PaymentAttempt(
        channelId = channelId,
        channelConfigurationId = channelConfigurationId,
        channelConfigurationSnapshot = channelConfigurationSnapshot,
        requestIdentity = requestIdentity,
        status = PaymentAttemptStatus.PROCESSING,
        initiatedAt = initiatedAt,
    ).also {
        attempts.add(it)
        attemptCount = attempts.size
        status = PaymentStatus.PROCESSING
    }
}

fun Payment.rejectAttemptStart(paymentAttemptId: PaymentAttemptId, failureCode: String, diagnosticSummary: String?) {
    val attempt = attempts.firstOrNull { it.id == paymentAttemptId }
        ?: error("payment attempt $paymentAttemptId does not belong to payment $id")
    attempt.status = PaymentAttemptStatus.FAILED
    attempt.finalResult = PaymentAttemptFinalResult.GATEWAY_REJECTED
    attempt.rejectionSummary = listOfNotNull(failureCode, diagnosticSummary).joinToString(": ")
    rejectedNotificationCount += 1
    lastRejectionSummary = attempt.rejectionSummary
    if (status != PaymentStatus.SUCCEEDED) status = PaymentStatus.FAILED
}

fun Payment.expire(now: LocalDateTime): PaymentExpiryOutcome {
    if (now.isBefore(expiresAt) || status in setOf(PaymentStatus.CLOSED, PaymentStatus.FAILED, PaymentStatus.SUCCEEDED)) {
        return PaymentExpiryOutcome(status, false, false, null)
    }
    val pending = attempts.filter {
        it.status == PaymentAttemptStatus.PROCESSING || it.status == PaymentAttemptStatus.RESULT_UNKNOWN
    }
    if (pending.isEmpty()) {
        status = PaymentStatus.CLOSED
        closedAt = now
        closeReason = "PAYMENT_EXPIRED_WITHOUT_PENDING_ATTEMPT"
        currentReviewEligibility()
        return PaymentExpiryOutcome(status, true, false, null)
    }
    pending.forEach {
        it.status = PaymentAttemptStatus.RESULT_UNKNOWN
        if (it.finalResult == null) it.finalResult = PaymentAttemptFinalResult.RESULT_UNKNOWN
    }
    status = PaymentStatus.RESULT_UNKNOWN
    val ids = pending.map { it.id.toString() }.sorted()
    val (review, opened) = openReview(
        PaymentReviewType.EXPIRY_RESULT_UNKNOWN,
        now,
        ids,
        emptyList(),
        "payment expired with pending attempts: ${ids.joinToString(",")}",
        "expiry:$expiresAt:${ids.joinToString(",")}",
    )
    return PaymentExpiryOutcome(status, false, opened, review.reviewIdentity)
}

fun Payment.currentReviewEligibility(): PaymentReviewEligibility {
    val blocking = reviewCases.filter {
        it.status == PaymentReviewStatus.OPEN && it.settlementImpact == PaymentReviewSettlementImpact.BLOCKS_SETTLEMENT
    }.sortedBy { it.reviewIdentity }
    reviewCount = reviewCases.size
    blockingReviewCount = blocking.size
    settlementBlocked = blocking.isNotEmpty()
    return PaymentReviewEligibility(
        settlementEligible = blocking.isEmpty(),
        blockingReviewIdentities = blocking.map { it.reviewIdentity },
        blockingReviewSummaries = blocking.map { "${it.type.name}: ${it.summary} (settlement-blocking)" },
    )
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
    settlementFeeRule: SettlementFeeRule? = null,
    merchantOrderSuccessAvailable: Boolean = true,
): ChannelResultRecordingOutcome {
    val normalizedResult = result.trim().uppercase()
    require(normalizedResult in setOf("SUCCESS", "FAILED", "UNKNOWN", "RESULT_UNKNOWN")) {
        "unsupported channel result: $result"
    }
    val normalizedCurrency = currency.trim().uppercase()
    val payloadIdentity = channelPayloadIdentity(
        channelId, channelTransactionId, amount, normalizedCurrency, normalizedResult, occurredAt
    )
    notificationReceiveCount += 1
    lastNotificationIdentity = notificationId
    lastNotificationReceivedAt = receivedAt

    val attempt = attempts.firstOrNull { it.id == paymentAttemptId }
    if (attempt == null) {
        val summary = "payment attempt $paymentAttemptId does not belong to payment $id"
        rejectedNotificationCount += 1
        lastRejectionSummary = summary
        return ChannelResultRecordingOutcome(
            status, null, notificationReceiveCount, ChannelResultDisposition.ATTEMPT_NOT_FOUND,
            summary, null, false, null, currentReviewEligibility().settlementEligible,
            merchantSuccessNotificationIntentState,
        )
    }
    attempt.notificationReceiveCount += 1
    attempt.notificationFirstReceivedAt = attempt.notificationFirstReceivedAt ?: receivedAt
    attempt.notificationLastReceivedAt = receivedAt

    val exact = attempt.paymentNotificationReceipts.firstOrNull {
        it.notificationIdentity == notificationId && it.payloadIdentity == payloadIdentity
    }
    if (exact != null) {
        exact.receiveCount += 1
        exact.lastReceivedAt = receivedAt
        val disposition = when {
            exact.decision == ChannelResultDisposition.CONFLICT -> ChannelResultDisposition.CONFLICT
            exact.accepted -> ChannelResultDisposition.ACCEPTED_DUPLICATE
            else -> ChannelResultDisposition.REJECTED_DUPLICATE
        }
        return outcome(
            attempt, disposition, exact.rejectionSummary, exact.conflictSummary,
            reviewForReceipt(payloadIdentity)?.reviewIdentity,
        )
    }

    val receipt = PaymentNotificationReceipt(
        notificationIdentity = notificationId,
        payloadIdentity = payloadIdentity,
        channelId = channelId,
        channelTransactionId = channelTransactionId,
        amount = amount,
        currency = normalizedCurrency,
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
    val sameNotificationOtherPayload = attempt.paymentNotificationReceipts.filter {
        it.notificationIdentity == notificationId && it.payloadIdentity != payloadIdentity
    }
    if (sameNotificationOtherPayload.isNotEmpty()) {
        val summary = "notification $notificationId reused with conflicting payload"
        receipt.decision = ChannelResultDisposition.CONFLICT
        receipt.conflictSummary = summary
        markConflict(attempt, summary)
        val (review, _) = openReview(
            PaymentReviewType.NOTIFICATION_PAYLOAD_CONFLICT,
            receivedAt,
            listOf(attempt.id.toString()),
            (sameNotificationOtherPayload.map { it.payloadIdentity } + payloadIdentity).distinct(),
            summary,
            "notification:$notificationId",
        )
        return outcome(attempt, ChannelResultDisposition.CONFLICT, conflict = summary, reviewIdentity = review.reviewIdentity)
    }

    val rejection = when {
        !verified -> verificationSummary ?: "channel verification failed"
        attempt.channelId != channelId -> "channel $channelId does not match attempt channel ${attempt.channelId}"
        this.amount.compareTo(amount) != 0 -> "notification amount $amount does not match payment amount ${this.amount}"
        this.currency != normalizedCurrency -> "notification currency $normalizedCurrency does not match payment currency ${this.currency}"
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
        return outcome(attempt, ChannelResultDisposition.REJECTED, rejection = rejection)
    }
    attempt.notificationIdentity = notificationId
    attempt.verifiedNotificationCount += 1
    attempt.verdictSummary = verificationSummary ?: "verified"
    receipt.verified = true

    if (normalizedResult == "SUCCESS" && !merchantOrderSuccessAvailable && !successFactFormed) {
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.CONFLICT
        receipt.conflictSummary = "merchant order already has an accepted success claim"
        attempt.channelTransactionId = channelTransactionId
        attempt.resultOccurredAt = occurredAt
        attempt.finalResult = PaymentAttemptFinalResult.SUCCESS
        attempt.status = PaymentAttemptStatus.SUCCEEDED
        status = PaymentStatus.FAILED
        markConflict(attempt, requireNotNull(receipt.conflictSummary))
        ensureMerchantNotificationIntent(PaymentNotificationIntentState.HELD_FOR_REVIEW)
        val (review, _) = openReview(
            PaymentReviewType.MERCHANT_ORDER_SUCCESS_CONFLICT,
            receivedAt,
            listOf(attempt.id.toString()),
            listOf(receipt.payloadIdentity),
            requireNotNull(receipt.conflictSummary),
            "merchant-order-claimed:${merchantId.trim()}:${merchantOrderNumber.trim()}",
        )
        return outcome(
            attempt,
            ChannelResultDisposition.CONFLICT,
            conflict = receipt.conflictSummary,
            reviewIdentity = review.reviewIdentity,
        )
    }

    return when {
        status == PaymentStatus.SUCCEEDED -> afterAcceptedSuccess(
            attempt, receipt, normalizedResult, channelTransactionId, occurredAt, receivedAt
        )
        status == PaymentStatus.CLOSED || status == PaymentStatus.FAILED -> afterTerminalWithoutSuccess(
            attempt, receipt, normalizedResult, channelTransactionId, occurredAt, receivedAt
        )
        status == PaymentStatus.RESULT_UNKNOWN -> afterUnknown(
            attempt, receipt, normalizedResult, channelTransactionId, occurredAt, settlementFeeRule
        )
        else -> initialResult(
            attempt, receipt, normalizedResult, channelTransactionId, occurredAt, receivedAt, settlementFeeRule
        )
    }
}

private fun Payment.initialResult(
    attempt: PaymentAttempt,
    receipt: PaymentNotificationReceipt,
    result: String,
    channelTransactionId: String,
    occurredAt: LocalDateTime,
    receivedAt: LocalDateTime,
    feeRule: SettlementFeeRule?,
): ChannelResultRecordingOutcome = when (result) {
    "SUCCESS" -> {
        acceptSuccess(attempt, receipt, channelTransactionId, occurredAt, requireNotNull(feeRule))
        outcome(attempt, ChannelResultDisposition.SUCCESS_ACCEPTED, successFormed = true)
    }
    "FAILED" -> {
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.FAILURE_ACCEPTED
        attempt.channelTransactionId = channelTransactionId
        attempt.resultOccurredAt = occurredAt
        attempt.finalResult = PaymentAttemptFinalResult.FAILED
        attempt.status = PaymentAttemptStatus.FAILED
        status = PaymentStatus.FAILED
        outcome(attempt, ChannelResultDisposition.FAILURE_ACCEPTED)
    }
    else -> {
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.CONFLICT
        attempt.channelTransactionId = channelTransactionId
        attempt.resultOccurredAt = occurredAt
        attempt.finalResult = PaymentAttemptFinalResult.RESULT_UNKNOWN
        attempt.status = PaymentAttemptStatus.RESULT_UNKNOWN
        status = PaymentStatus.RESULT_UNKNOWN
        val summary = "channel result remains unknown for attempt ${attempt.id}"
        receipt.conflictSummary = summary
        markConflict(attempt, summary)
        val (review, _) = openReview(
            PaymentReviewType.EXPIRY_RESULT_UNKNOWN,
            receivedAt,
            listOf(attempt.id.toString()),
            listOf(receipt.payloadIdentity),
            summary,
            "unknown:${attempt.id}",
        )
        outcome(attempt, ChannelResultDisposition.CONFLICT, conflict = summary, reviewIdentity = review.reviewIdentity)
    }
}

private fun Payment.afterUnknown(
    attempt: PaymentAttempt,
    receipt: PaymentNotificationReceipt,
    result: String,
    channelTransactionId: String,
    occurredAt: LocalDateTime,
    feeRule: SettlementFeeRule?,
): ChannelResultRecordingOutcome = when (result) {
    "SUCCESS" -> {
        acceptSuccess(attempt, receipt, channelTransactionId, occurredAt, requireNotNull(feeRule))
        resolveUnknownReviews(attempt, receipt, PaymentReviewDecisionType.SYSTEM_ACCEPT_SUCCESS, occurredAt)
        outcome(attempt, ChannelResultDisposition.SUCCESS_ACCEPTED, successFormed = true)
    }
    "FAILED" -> {
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.FAILURE_ACCEPTED
        attempt.channelTransactionId = channelTransactionId
        attempt.resultOccurredAt = occurredAt
        attempt.finalResult = PaymentAttemptFinalResult.FAILED
        attempt.status = PaymentAttemptStatus.FAILED
        status = PaymentStatus.FAILED
        resolveUnknownReviews(attempt, receipt, PaymentReviewDecisionType.SYSTEM_CONFIRM_FAILURE, occurredAt)
        outcome(attempt, ChannelResultDisposition.FAILURE_ACCEPTED)
    }
    else -> {
        val summary = "channel result remains unknown for attempt ${attempt.id}"
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.CONFLICT
        receipt.conflictSummary = summary
        markConflict(attempt, summary)
        outcome(
            attempt,
            ChannelResultDisposition.CONFLICT,
            conflict = summary,
            reviewIdentity = reviewCases.firstOrNull {
                it.status == PaymentReviewStatus.OPEN &&
                    it.type == PaymentReviewType.EXPIRY_RESULT_UNKNOWN &&
                    it.triggeringAttemptIdentities.csvContains(attempt.id.toString())
            }?.reviewIdentity,
        )
    }
}

private fun Payment.afterTerminalWithoutSuccess(
    attempt: PaymentAttempt,
    receipt: PaymentNotificationReceipt,
    result: String,
    channelTransactionId: String,
    occurredAt: LocalDateTime,
    receivedAt: LocalDateTime,
): ChannelResultRecordingOutcome {
    if (result != "SUCCESS") {
        val summary = "${result.lowercase()} result arrived after terminal payment status $status"
        receipt.decision = ChannelResultDisposition.CONFLICT
        receipt.conflictSummary = summary
        markConflict(attempt, summary)
        return outcome(attempt, ChannelResultDisposition.CONFLICT, conflict = summary)
    }
    val terminalStatus = status
    receipt.accepted = true
    receipt.decision = ChannelResultDisposition.CONFLICT
    receipt.conflictSummary = "trusted success arrived after terminal payment status $terminalStatus"
    attempt.channelTransactionId = channelTransactionId
    attempt.resultOccurredAt = occurredAt
    attempt.finalResult = PaymentAttemptFinalResult.SUCCESS
    attempt.status = PaymentAttemptStatus.SUCCEEDED
    markConflict(attempt, requireNotNull(receipt.conflictSummary))
    ensureMerchantNotificationIntent(PaymentNotificationIntentState.HELD_FOR_REVIEW)
    val reviewType = if (terminalStatus == PaymentStatus.FAILED) {
        PaymentReviewType.SUCCESS_AFTER_FAILURE_CONFLICT
    } else {
        PaymentReviewType.LATE_SUCCESS_AFTER_TERMINAL
    }
    val (review, _) = openReview(
        reviewType,
        receivedAt,
        listOf(attempt.id.toString()),
        listOf(receipt.payloadIdentity),
        requireNotNull(receipt.conflictSummary),
        "terminal:$terminalStatus:${attempt.id}:${receipt.payloadIdentity}",
    )
    return outcome(
        attempt,
        ChannelResultDisposition.CONFLICT,
        conflict = receipt.conflictSummary,
        reviewIdentity = review.reviewIdentity,
    )
}

private fun Payment.afterAcceptedSuccess(
    attempt: PaymentAttempt,
    receipt: PaymentNotificationReceipt,
    result: String,
    channelTransactionId: String,
    occurredAt: LocalDateTime,
    receivedAt: LocalDateTime,
): ChannelResultRecordingOutcome {
    val firstSuccess = attempts.firstOrNull {
        it.status == PaymentAttemptStatus.SUCCEEDED && it.id != attempt.id
    }
    val type: PaymentReviewType
    val summary: String
    if (result == "SUCCESS") {
        receipt.accepted = true
        attempt.channelTransactionId = channelTransactionId
        attempt.resultOccurredAt = occurredAt
        attempt.finalResult = PaymentAttemptFinalResult.SUCCESS
        attempt.status = PaymentAttemptStatus.SUCCEEDED
        type = PaymentReviewType.MULTIPLE_ATTEMPT_SUCCESS
        summary = if (firstSuccess == null) {
            "additional success evidence arrived after accepted success for attempt ${attempt.id}"
        } else {
            "multiple payment attempts contain trustworthy success evidence: ${firstSuccess.id},${attempt.id}"
        }
    } else {
        if (attempt.status != PaymentAttemptStatus.SUCCEEDED) {
            attempt.channelTransactionId = channelTransactionId
            attempt.resultOccurredAt = occurredAt
            attempt.finalResult = if (result == "FAILED") {
                PaymentAttemptFinalResult.FAILED
            } else {
                PaymentAttemptFinalResult.RESULT_UNKNOWN
            }
            attempt.status = if (result == "FAILED") {
                PaymentAttemptStatus.FAILED
            } else {
                PaymentAttemptStatus.RESULT_UNKNOWN
            }
        }
        type = PaymentReviewType.FAILURE_OR_UNKNOWN_AFTER_SUCCESS
        summary = "${result.lowercase()} evidence arrived after accepted payment success"
    }
    receipt.decision = ChannelResultDisposition.CONFLICT
    receipt.conflictSummary = summary
    markConflict(attempt, summary)
    ensureMerchantNotificationIntent(PaymentNotificationIntentState.HELD_FOR_REVIEW)
    val attemptIds = listOfNotNull(firstSuccess?.id?.toString(), attempt.id.toString()).distinct().sorted()
    val discriminator = if (type == PaymentReviewType.MULTIPLE_ATTEMPT_SUCCESS) {
        "multiple-success:${attemptIds.joinToString(",")}"
    } else {
        "after-success:${attempt.id}:$result"
    }
    val (review, _) = openReview(
        type,
        receivedAt,
        attemptIds,
        listOf(receipt.payloadIdentity),
        summary,
        discriminator,
    )
    return outcome(attempt, ChannelResultDisposition.CONFLICT, conflict = summary, reviewIdentity = review.reviewIdentity)
}

private fun Payment.acceptSuccess(
    attempt: PaymentAttempt,
    receipt: PaymentNotificationReceipt,
    channelTransactionId: String,
    occurredAt: LocalDateTime,
    feeRule: SettlementFeeRule,
) {
    check(!successFactFormed && status != PaymentStatus.SUCCEEDED)
    receipt.accepted = true
    receipt.decision = ChannelResultDisposition.SUCCESS_ACCEPTED
    attempt.channelTransactionId = channelTransactionId
    attempt.resultOccurredAt = occurredAt
    attempt.finalResult = PaymentAttemptFinalResult.SUCCESS
    attempt.status = PaymentAttemptStatus.SUCCEEDED
    status = PaymentStatus.SUCCEEDED
    succeededAt = occurredAt
    this.channelTransactionId = channelTransactionId
    freezeSettlementFee(feeRule, occurredAt)
    successFactFormed = true
    merchantOrderSuccessIdentity = "merchant:${merchantId.trim()}:order:${merchantOrderNumber.trim()}"
    ensureMerchantNotificationIntent(PaymentNotificationIntentState.READY)
}

fun Payment.adjudicateReview(
    reviewIdentity: String,
    decisionIdentity: String,
    decision: PaymentReviewDecisionType,
    operatorIdentity: String,
    operatorRole: String,
    authorized: Boolean,
    reason: String,
    evidence: String,
    decidedAt: LocalDateTime,
    eligibilityImpact: PaymentReviewEligibilityImpact,
    remediationReference: String?,
    settlementFeeRule: SettlementFeeRule? = null,
): PaymentReviewAdjudicationOutcome {
    require(operatorIdentity.isNotBlank())
    require(operatorRole.isNotBlank())
    require(reason.isNotBlank())
    require(evidence.isNotBlank())
    check(authorized) { "REVIEW_UNAUTHORIZED" }
    val review = reviewCases.firstOrNull {
        it.reviewIdentity == reviewIdentity || runCatching { it.id.toString() }.getOrNull() == reviewIdentity
    } ?: throw IllegalArgumentException("REVIEW_NOT_FOUND")
    review.paymentReviewDecisions.firstOrNull { it.decisionIdentity == decisionIdentity }?.let { existing ->
        check(
            existing.decision == decision &&
                existing.operatorIdentity == operatorIdentity &&
                existing.operatorRole == operatorRole &&
                existing.authorizationOutcome &&
                existing.reason == reason &&
                existing.evidence == evidence &&
                existing.decidedAt == decidedAt &&
                existing.eligibilityImpact == eligibilityImpact &&
                existing.remediationReference == remediationReference
        ) { "REVIEW_DECISION_IDEMPOTENCY_CONFLICT" }
        return adjudicationOutcome(review)
    }
    check(review.status == PaymentReviewStatus.OPEN) { "REVIEW_DECISION_NOT_ALLOWED" }

    when (decision) {
        PaymentReviewDecisionType.ACCEPT_LATE_SUCCESS -> {
            check(review.type in lateSuccessReviewTypes) { "REVIEW_DECISION_NOT_ALLOWED" }
            check(!successFactFormed && status != PaymentStatus.SUCCEEDED) { "REVIEW_DECISION_NOT_ALLOWED" }
            val evidencePair = attempts.asSequence()
                .flatMap { attempt -> attempt.paymentNotificationReceipts.asSequence().map { attempt to it } }
                .filter { (_, receipt) -> receipt.verified && receipt.accepted && receipt.result == "SUCCESS" }
                .maxByOrNull { (_, receipt) -> receipt.occurredAt }
                ?: error("REVIEW_DECISION_NOT_ALLOWED")
            acceptSuccess(
                evidencePair.first,
                evidencePair.second,
                evidencePair.second.channelTransactionId,
                evidencePair.second.occurredAt,
                requireNotNull(settlementFeeRule) { "REVIEW_DECISION_NOT_ALLOWED" },
            )
        }
        PaymentReviewDecisionType.CONFIRM_FAILURE -> {
            check(review.type == PaymentReviewType.EXPIRY_RESULT_UNKNOWN) { "REVIEW_DECISION_NOT_ALLOWED" }
            check(!successFactFormed && status == PaymentStatus.RESULT_UNKNOWN) { "REVIEW_DECISION_NOT_ALLOWED" }
            status = PaymentStatus.FAILED
            attempts.filter { it.status == PaymentAttemptStatus.RESULT_UNKNOWN }.forEach {
                it.status = PaymentAttemptStatus.FAILED
                it.finalResult = PaymentAttemptFinalResult.FAILED
                it.resultOccurredAt = decidedAt
            }
        }
        PaymentReviewDecisionType.KEEP_CURRENT_TERMINAL -> {
            check(review.type in terminalConflictReviewTypes) { "REVIEW_DECISION_NOT_ALLOWED" }
            check(status == PaymentStatus.CLOSED || status == PaymentStatus.FAILED) { "REVIEW_DECISION_NOT_ALLOWED" }
        }
        PaymentReviewDecisionType.KEEP_ACCEPTED_SUCCESS_WITH_REMEDIATION -> {
            check(review.type in acceptedSuccessConflictReviewTypes) { "REVIEW_DECISION_NOT_ALLOWED" }
            check(status == PaymentStatus.SUCCEEDED && successFactFormed && !remediationReference.isNullOrBlank()) {
                "REVIEW_DECISION_NOT_ALLOWED"
            }
            requireSettlementFeeSnapshot()
        }
        PaymentReviewDecisionType.SYSTEM_ACCEPT_SUCCESS,
        PaymentReviewDecisionType.SYSTEM_CONFIRM_FAILURE -> error("REVIEW_DECISION_NOT_ALLOWED")
    }

    review.paymentReviewDecisions.add(
        PaymentReviewDecision(
            decisionIdentity = decisionIdentity,
            decision = decision,
            operatorIdentity = operatorIdentity,
            operatorRole = operatorRole,
            authorizationOutcome = true,
            reason = reason,
            evidence = evidence,
            decidedAt = decidedAt,
            eligibilityImpact = eligibilityImpact,
            remediationReference = remediationReference,
        )
    )
    applyEligibilityDecision(review, eligibilityImpact, decidedAt)
    merchantSuccessNotificationIntentState = when {
        eligibilityImpact == PaymentReviewEligibilityImpact.KEEP_BLOCKED && merchantSuccessNotificationIntentIdentity != null ->
            PaymentNotificationIntentState.HELD_FOR_REVIEW
        eligibilityImpact == PaymentReviewEligibilityImpact.ALLOW_SETTLEMENT && successFactFormed ->
            PaymentNotificationIntentState.READY
        eligibilityImpact == PaymentReviewEligibilityImpact.ALLOW_SETTLEMENT && merchantSuccessNotificationIntentIdentity != null ->
            PaymentNotificationIntentState.CANCELLED
        else -> merchantSuccessNotificationIntentState
    }
    return adjudicationOutcome(review)
}


private val lateSuccessReviewTypes = setOf(
    PaymentReviewType.LATE_SUCCESS_AFTER_TERMINAL,
    PaymentReviewType.SUCCESS_AFTER_FAILURE_CONFLICT,
)

private val terminalConflictReviewTypes = setOf(
    PaymentReviewType.LATE_SUCCESS_AFTER_TERMINAL,
    PaymentReviewType.SUCCESS_AFTER_FAILURE_CONFLICT,
    PaymentReviewType.MERCHANT_ORDER_SUCCESS_CONFLICT,
)

private val acceptedSuccessConflictReviewTypes = setOf(
    PaymentReviewType.MULTIPLE_ATTEMPT_SUCCESS,
    PaymentReviewType.FAILURE_OR_UNKNOWN_AFTER_SUCCESS,
    PaymentReviewType.NOTIFICATION_PAYLOAD_CONFLICT,
)

private fun Payment.adjudicationOutcome(review: PaymentReviewCase): PaymentReviewAdjudicationOutcome {
    val eligibility = currentReviewEligibility()
    return PaymentReviewAdjudicationOutcome(
        status,
        review.status,
        review.paymentReviewDecisions.size,
        eligibility.settlementEligible,
        merchantSuccessNotificationIntentState,
    )
}

private fun Payment.resolveUnknownReviews(
    attempt: PaymentAttempt,
    receipt: PaymentNotificationReceipt,
    decision: PaymentReviewDecisionType,
    decidedAt: LocalDateTime,
) {
    reviewCases.filter {
        it.status == PaymentReviewStatus.OPEN &&
            it.type == PaymentReviewType.EXPIRY_RESULT_UNKNOWN &&
            it.triggeringAttemptIdentities.csvContains(attempt.id.toString())
    }.forEach { review ->
        val identity = "system:${review.reviewIdentity}:${decision.name}"
        if (review.paymentReviewDecisions.none { it.decisionIdentity == identity }) {
            review.paymentReviewDecisions.add(
                PaymentReviewDecision(
                    decisionIdentity = identity,
                    decision = decision,
                    operatorIdentity = "SYSTEM",
                    operatorRole = "PAYMENT_RESULT_ADJUDICATOR",
                    authorizationOutcome = true,
                    reason = "trustworthy channel result resolved result-unknown review",
                    evidence = "receipt:${receipt.payloadIdentity}",
                    decidedAt = decidedAt,
                    eligibilityImpact = PaymentReviewEligibilityImpact.ALLOW_SETTLEMENT,
                    remediationReference = null,
                )
            )
        }
        applyEligibilityDecision(review, PaymentReviewEligibilityImpact.ALLOW_SETTLEMENT, decidedAt)
    }
    currentReviewEligibility()
}

private fun Payment.openReview(
    type: PaymentReviewType,
    openedAt: LocalDateTime,
    attemptIdentities: List<String>,
    receiptIdentities: List<String>,
    summary: String,
    discriminator: String,
): Pair<PaymentReviewCase, Boolean> {
    val identity = "payment-review:${sha256("$id|${type.name}|$discriminator")}"
    reviewCases.firstOrNull { it.reviewIdentity == identity }?.let {
        currentReviewEligibility()
        return it to false
    }
    val review = PaymentReviewCase(
        reviewIdentity = identity,
        type = type,
        status = PaymentReviewStatus.OPEN,
        openedAt = openedAt,
        triggeringPaymentStatus = status,
        triggeringAttemptIdentities = attemptIdentities.distinct().sorted().joinToString(",").ifBlank { null },
        triggeringReceiptIdentities = receiptIdentities.distinct().sorted().joinToString(",").ifBlank { null },
        summary = summary,
        settlementImpact = PaymentReviewSettlementImpact.BLOCKS_SETTLEMENT,
        resolvedAt = null,
    )
    reviewCases.add(review)
    currentReviewEligibility()
    return review to true
}

private fun Payment.reviewForReceipt(payloadIdentity: String): PaymentReviewCase? =
    reviewCases.firstOrNull { it.triggeringReceiptIdentities.csvContains(payloadIdentity) }

private fun Payment.applyEligibilityDecision(
    review: PaymentReviewCase,
    impact: PaymentReviewEligibilityImpact,
    decidedAt: LocalDateTime,
) {
    if (impact == PaymentReviewEligibilityImpact.ALLOW_SETTLEMENT) {
        review.status = PaymentReviewStatus.RESOLVED
        review.settlementImpact = PaymentReviewSettlementImpact.ALLOWS_SETTLEMENT
        review.resolvedAt = decidedAt
    } else {
        review.status = PaymentReviewStatus.OPEN
        review.settlementImpact = PaymentReviewSettlementImpact.BLOCKS_SETTLEMENT
        review.resolvedAt = null
    }
}

private fun String?.csvContains(value: String): Boolean =
    this?.split(',')?.any { it == value } == true

private fun Payment.markConflict(attempt: PaymentAttempt, summary: String) {
    conflictingNotificationCount += 1
    lastConflictSummary = summary
    attempt.conflictingNotificationCount += 1
    attempt.conflictSummary = summary
}

private fun Payment.ensureMerchantNotificationIntent(state: PaymentNotificationIntentState) {
    if (merchantSuccessNotificationIntentIdentity == null) {
        merchantSuccessNotificationIntentIdentity = "payment:$id:merchant-success:v1"
        merchantSuccessNotificationIntentCount += 1
    }
    merchantSuccessNotificationIntentState = state
}

private fun Payment.freezeSettlementFee(rule: SettlementFeeRule, formedAt: LocalDateTime) {
    check(settlementFeeFactIdentity == null)
    val fee = amount
        .multiply(BigDecimal.valueOf(rule.basisPoints.toLong()))
        .divide(BigDecimal.valueOf(10_000L))
        .add(rule.fixedFeeAmount)
        .setScale(rule.currencyPrecision, rule.roundingMode)
    settlementFeeFactIdentity = "payment:$id:settlement-fee"
    settlementFeeBasisPoints = rule.basisPoints
    settlementFixedFeeAmount = rule.fixedFeeAmount
    settlementFeeRoundingMode = rule.roundingMode.name
    settlementFeeCurrencyPrecision = rule.currencyPrecision
    settlementFeeCalculationAmount = amount
    settlementFeeAmount = fee
    settlementFeeFormedAt = formedAt
}

private fun Payment.requireSettlementFeeSnapshot() {
    check(
        settlementFeeFactIdentity != null &&
            settlementFeeBasisPoints != null &&
            settlementFixedFeeAmount != null &&
            settlementFeeRoundingMode != null &&
            settlementFeeCurrencyPrecision != null &&
            settlementFeeCalculationAmount != null &&
            settlementFeeAmount != null &&
            settlementFeeFormedAt != null
    )
}

private fun channelPayloadIdentity(
    channelId: String,
    channelTransactionId: String,
    amount: BigDecimal,
    currency: String,
    result: String,
    occurredAt: LocalDateTime,
): String = sha256(
    listOf(
        channelId.trim(),
        channelTransactionId.trim(),
        amount.stripTrailingZeros().toPlainString(),
        currency.trim().uppercase(),
        result.trim().uppercase(),
        occurredAt.toString(),
    ).joinToString("|")
)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun Payment.outcome(
    attempt: PaymentAttempt,
    disposition: ChannelResultDisposition,
    rejection: String? = null,
    conflict: String? = null,
    reviewIdentity: String? = null,
    successFormed: Boolean = false,
): ChannelResultRecordingOutcome {
    val eligibility = currentReviewEligibility()
    return ChannelResultRecordingOutcome(
        paymentStatus = status,
        attemptStatus = attempt.status,
        notificationReceiveCount = notificationReceiveCount,
        disposition = disposition,
        rejectionSummary = rejection,
        conflictSummary = conflict,
        successFactFormedNow = successFormed,
        reviewIdentity = reviewIdentity,
        settlementEligible = eligibility.settlementEligible,
        notificationIntentState = merchantSuccessNotificationIntentState,
    )
}

fun Payment.onCreate() = Unit
fun Payment.onDeleted() = Unit

val Payment.refundableAmount: BigDecimal
    get() = amount.subtract(reservedRefundAmount).subtract(successfulRefundAmount)

fun Payment.reserveRefund(amount: BigDecimal) {
    require(status == PaymentStatus.SUCCEEDED) { "payment $id is not successful" }
    require(currentReviewEligibility().settlementEligible) { "payment $id has unresolved payment review" }
    require(amount > BigDecimal.ZERO)
    if (refundableAmount < amount) {
        throw RefundBudgetConflictException("payment $id has only $refundableAmount refundable amount")
    }
    reservedRefundAmount = reservedRefundAmount.add(amount)
}

fun Payment.releaseRefundReservation(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO)
    require(reservedRefundAmount >= amount)
    reservedRefundAmount = reservedRefundAmount.subtract(amount)
}

fun Payment.convertRefundReservationToSuccess(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO)
    require(reservedRefundAmount >= amount)
    reservedRefundAmount = reservedRefundAmount.subtract(amount)
    successfulRefundAmount = successfulRefundAmount.add(amount)
}
