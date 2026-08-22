package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionFinalResult
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events.MerchantSettlementActivationRequestedDomainEvent
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events.MerchantSettlementCompletedDomainEvent
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementResultRecordingOutcome
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime

private fun stableIdentity(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("|").toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
    .take(64)

private const val SETTLEMENT_OPERATOR_ROLE = "SETTLEMENT_OPERATOR"

fun MerchantSettlement.onCreate() {
    require(periodEnd > periodStart) { "settlement periodEnd must be after periodStart" }
    require(merchantId.isNotBlank()) { "merchantId must not be blank" }
    require(channelId.isNotBlank()) { "channelId must not be blank" }
    require(currency.isNotBlank()) { "currency must not be blank" }
    require(scopeIdentity.isNotBlank()) { "scopeIdentity must not be blank" }
    requireTotalsMatchLines()
}

fun MerchantSettlement.onDeleted() {
}

fun MerchantSettlement.confirmComposition(
    operatorIdentity: String,
    operatorRole: String,
    confirmedAt: LocalDateTime,
): MerchantSettlementStatus {
    requireAuthorized(operatorIdentity, operatorRole)
    require(status in setOf(
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
    )) { "settlement $id cannot be confirmed while $status" }
    require(!compositionFrozen) { "settlement $id composition is already frozen" }
    require(settlementLines.isNotEmpty()) { "settlement $id has no settlement lines" }
    requireTotalsMatchLines()

    compositionFrozen = true
    confirmedBy = operatorIdentity.trim()
    this.confirmedAt = confirmedAt
    status = when {
        netAmount.signum() < 0 -> MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED
        netAmount.signum() == 0 -> {
            formSettledSuccess(confirmedAt)
            MerchantSettlementStatus.SUCCEEDED
        }
        else -> MerchantSettlementStatus.CONFIRMED
    }
    return status
}

fun MerchantSettlement.startExecutionAttempt(
    operatorIdentity: String,
    operatorRole: String,
    requestedAt: LocalDateTime,
    reviewAfterMinutes: Int,
    executionGroupIdentity: String,
    requestIdentity: String,
): SettlementExecutionAttempt {
    requireAuthorized(operatorIdentity, operatorRole)
    require(compositionFrozen) { "settlement $id composition is not confirmed" }
    require(netAmount.signum() > 0) { "settlement $id has no positive amount to transfer" }
    require(status != MerchantSettlementStatus.RESULT_UNKNOWN) {
        "settlement $id cannot create a new attempt while result is unknown"
    }
    settlementExecutionAttempts.firstOrNull { it.status == SettlementExecutionAttemptStatus.PROCESSING }?.let { return it }
    require(status == MerchantSettlementStatus.CONFIRMED || status == MerchantSettlementStatus.FAILED) {
        "settlement $id cannot start execution while $status"
    }
    if (status == MerchantSettlementStatus.FAILED) {
        require(settlementExecutionAttempts.lastOrNull()?.finalResult in setOf(
            SettlementExecutionFinalResult.FAILED,
            SettlementExecutionFinalResult.GATEWAY_REJECTED,
        )) { "settlement $id may retry only after an explicit failed attempt" }
    }
    require(reviewAfterMinutes > 0) { "reviewAfterMinutes must be positive" }
    require(executionGroupIdentity.isNotBlank()) { "executionGroupIdentity must not be blank" }
    require(requestIdentity.isNotBlank()) { "requestIdentity must not be blank" }
    if (this.executionGroupIdentity != null) {
        require(this.executionGroupIdentity == executionGroupIdentity) {
            "settlement $id execution group cannot change"
        }
    } else {
        this.executionGroupIdentity = executionGroupIdentity
    }

    val attempt = SettlementExecutionAttempt(
        attemptSequence = settlementExecutionAttempts.size + 1,
        executionGroupIdentity = executionGroupIdentity,
        requestIdentity = requestIdentity,
        channelId = channelId,
        status = SettlementExecutionAttemptStatus.PROCESSING,
        initiatedAt = requestedAt,
        reviewAfterMinutesSnapshot = reviewAfterMinutes,
        reviewAfterAt = requestedAt.plusMinutes(reviewAfterMinutes.toLong()),
        amount = netAmount,
        currency = currency,
    )
    settlementExecutionAttempts.add(attempt)
    return attempt
}

fun MerchantSettlement.markExecutionAccepted(
    attemptId: SettlementExecutionAttemptId,
    externalSettlementIdentity: String,
    acceptedAt: LocalDateTime,
) {
    val attempt = requireAttempt(attemptId)
    require(attempt.status == SettlementExecutionAttemptStatus.PROCESSING) {
        "settlement attempt $attemptId cannot be accepted while ${attempt.status}"
    }
    require(externalSettlementIdentity.isNotBlank()) { "externalSettlementIdentity must not be blank" }
    attempt.externalSettlementIdentity = externalSettlementIdentity
    attempt.acceptedAt = acceptedAt
    this.externalSettlementIdentity = externalSettlementIdentity
    status = MerchantSettlementStatus.PROCESSING
}

fun MerchantSettlement.rejectExecutionStart(
    attemptId: SettlementExecutionAttemptId,
    failureCode: String,
    diagnosticSummary: String?,
) {
    val attempt = requireAttempt(attemptId)
    val summary = listOfNotNull(failureCode.takeIf { it.isNotBlank() }, diagnosticSummary?.takeIf { it.isNotBlank() })
        .joinToString(": ")
        .ifBlank { "settlement transfer was rejected" }
    attempt.status = SettlementExecutionAttemptStatus.FAILED
    attempt.finalResult = SettlementExecutionFinalResult.GATEWAY_REJECTED
    attempt.rejectionSummary = summary
    lastRejectionSummary = summary
    status = MerchantSettlementStatus.FAILED
}

fun MerchantSettlement.recordSettlementResult(
    attemptId: SettlementExecutionAttemptId,
    notificationIdentity: String,
    payloadFingerprint: String,
    channelId: String,
    executionGroupIdentity: String,
    requestIdentity: String,
    externalSettlementIdentity: String,
    amount: BigDecimal,
    currency: String,
    result: String,
    resultCode: String?,
    occurredAt: LocalDateTime,
    receivedAt: LocalDateTime,
    verified: Boolean,
    verificationSummary: String?,
): SettlementResultRecordingOutcome {
    val normalizedResult = result.trim().uppercase()
    require(normalizedResult in setOf("SUCCESS", "FAILED", "UNKNOWN")) {
        "unsupported settlement result: $result"
    }
    require(notificationIdentity.isNotBlank()) { "notificationIdentity must not be blank" }
    require(payloadFingerprint.isNotBlank()) { "payloadFingerprint must not be blank" }
    val attempt = settlementExecutionAttempts.firstOrNull { it.id == attemptId }
        ?: run {
            val rejection = "settlement attempt $attemptId does not belong to settlement $id"
            lastRejectionSummary = rejection
            return resultOutcome(null, SettlementResultDisposition.ATTEMPT_NOT_FOUND, rejectionSummary = rejection)
        }

    attempt.notificationReceiveCount += 1
    attempt.notificationFirstReceivedAt = attempt.notificationFirstReceivedAt ?: receivedAt
    attempt.notificationLastReceivedAt = receivedAt
    val existing = attempt.settlementResultReceipts.firstOrNull { it.notificationIdentity == notificationIdentity }
    if (existing != null) {
        existing.receiveCount += 1
        existing.lastReceivedAt = receivedAt
        if (existing.payloadFingerprint != payloadFingerprint) {
            return markResultConflict(attempt, existing, "notification $notificationIdentity was reused with a conflicting payload")
        }
        val disposition = when {
            existing.decision == SettlementResultDisposition.CONFLICT -> SettlementResultDisposition.CONFLICT
            existing.accepted -> SettlementResultDisposition.ACCEPTED_DUPLICATE
            else -> SettlementResultDisposition.REJECTED_DUPLICATE
        }
        return resultOutcome(
            attempt,
            disposition,
            rejectionSummary = existing.rejectionSummary,
            conflictSummary = existing.conflictSummary,
        )
    }

    val receipt = SettlementResultReceipt(
        notificationIdentity = notificationIdentity,
        payloadFingerprint = payloadFingerprint,
        channelId = channelId,
        executionGroupIdentity = executionGroupIdentity,
        requestIdentity = requestIdentity,
        externalSettlementIdentity = externalSettlementIdentity,
        amount = amount,
        currency = currency.trim().uppercase(),
        result = normalizedResult,
        resultCode = resultCode,
        occurredAt = occurredAt,
        firstReceivedAt = receivedAt,
        lastReceivedAt = receivedAt,
        verified = verified,
        accepted = false,
        decision = SettlementResultDisposition.RECEIVED,
        verdictSummary = verificationSummary,
    )
    attempt.settlementResultReceipts.add(receipt)

    val rejection = when {
        !verified -> verificationSummary ?: "settlement result verification failed"
        attempt.channelId != channelId -> "channel $channelId does not match attempt channel ${attempt.channelId}"
        attempt.executionGroupIdentity != executionGroupIdentity -> "execution group identity does not match the attempt"
        attempt.requestIdentity != requestIdentity -> "request identity does not match the attempt"
        attempt.externalSettlementIdentity != null && attempt.externalSettlementIdentity != externalSettlementIdentity ->
            "external settlement identity does not match the accepted attempt"
        attempt.amount.compareTo(amount) != 0 -> "result amount $amount does not match settlement amount ${attempt.amount}"
        attempt.currency != currency.trim().uppercase() -> "result currency does not match settlement currency ${attempt.currency}"
        else -> null
    }
    if (rejection != null) {
        receipt.decision = SettlementResultDisposition.REJECTED
        receipt.rejectionSummary = rejection
        attempt.rejectedNotificationCount += 1
        attempt.rejectionSummary = rejection
        lastRejectionSummary = rejection
        return resultOutcome(attempt, SettlementResultDisposition.REJECTED, rejectionSummary = rejection)
    }

    val priorFinal = attempt.finalResult
    if (priorFinal != null) {
        val sameFinal = priorFinal.matches(normalizedResult)
        if (!sameFinal) {
            return markResultConflict(attempt, receipt, "late $normalizedResult result conflicts with finalized ${priorFinal.name} attempt")
        }
        receipt.accepted = true
        receipt.decision = SettlementResultDisposition.ACCEPTED_DUPLICATE
        return resultOutcome(attempt, SettlementResultDisposition.ACCEPTED_DUPLICATE)
    }

    receipt.accepted = true
    attempt.verifiedNotificationCount += 1
    attempt.verdictSummary = verificationSummary ?: "verified"
    attempt.externalSettlementIdentity = externalSettlementIdentity
    attempt.resultOccurredAt = occurredAt
    this.externalSettlementIdentity = externalSettlementIdentity

    return when (normalizedResult) {
        "SUCCESS" -> {
            receipt.decision = SettlementResultDisposition.SUCCESS_ACCEPTED
            attempt.status = SettlementExecutionAttemptStatus.SUCCEEDED
            attempt.finalResult = SettlementExecutionFinalResult.SUCCESS
            val formedNow = formSettledSuccess(occurredAt)
            resultOutcome(attempt, SettlementResultDisposition.SUCCESS_ACCEPTED, settledFactFormedNow = formedNow)
        }
        "FAILED" -> {
            receipt.decision = SettlementResultDisposition.FAILURE_ACCEPTED
            attempt.status = SettlementExecutionAttemptStatus.FAILED
            attempt.finalResult = SettlementExecutionFinalResult.FAILED
            status = MerchantSettlementStatus.FAILED
            resultOutcome(attempt, SettlementResultDisposition.FAILURE_ACCEPTED)
        }
        else -> {
            receipt.decision = SettlementResultDisposition.UNKNOWN_ACCEPTED
            attempt.status = SettlementExecutionAttemptStatus.RESULT_UNKNOWN
            attempt.finalResult = SettlementExecutionFinalResult.UNKNOWN
            status = MerchantSettlementStatus.RESULT_UNKNOWN
            lastReviewSummary = "settlement result is unknown and requires review after ${attempt.reviewAfterAt}"
            resultOutcome(attempt, SettlementResultDisposition.UNKNOWN_ACCEPTED, reviewSummary = lastReviewSummary)
        }
    }
}

fun MerchantSettlement.markUnknownReviewRequired(reviewedAt: LocalDateTime): Boolean {
    val overdue = settlementExecutionAttempts.filter {
        it.status == SettlementExecutionAttemptStatus.RESULT_UNKNOWN && it.reviewAfterAt <= reviewedAt
    }
    if (overdue.isEmpty()) return false
    overdue.forEach { it.status = SettlementExecutionAttemptStatus.REVIEW_REQUIRED }
    lastReviewSummary = "unknown settlement result exceeded the frozen review threshold at $reviewedAt"
    if (!settledFactFormed) status = MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED
    return true
}

fun MerchantSettlement.adjudicateUnknownResult(
    attemptId: SettlementExecutionAttemptId,
    operatorIdentity: String,
    operatorRole: String,
    finalResult: String,
    adjudicatedAt: LocalDateTime,
    evidence: String,
): SettlementResultRecordingOutcome {
    requireAuthorized(operatorIdentity, operatorRole)
    require(evidence.isNotBlank()) { "manual adjudication evidence must not be blank" }
    val normalizedResult = finalResult.trim().uppercase()
    require(normalizedResult in setOf("SUCCESS", "FAILED")) {
        "manual adjudication result must be SUCCESS or FAILED"
    }
    val attempt = requireAttempt(attemptId)
    require(attempt.status in setOf(
        SettlementExecutionAttemptStatus.RESULT_UNKNOWN,
        SettlementExecutionAttemptStatus.REVIEW_REQUIRED,
        SettlementExecutionAttemptStatus.CONFLICT_REVIEW_REQUIRED,
    )) { "settlement attempt $attemptId cannot be manually adjudicated while ${attempt.status}" }
    require(attempt.finalResult == SettlementExecutionFinalResult.UNKNOWN) {
        "settlement attempt $attemptId does not have an unknown final result"
    }
    val externalIdentity = requireNotNull(attempt.externalSettlementIdentity) {
        "settlement attempt $attemptId has no external settlement identity"
    }
    val summary = "manual adjudication by ${operatorIdentity.trim()}: ${evidence.trim()}"
    val receipt = SettlementResultReceipt(
        notificationIdentity = "manual-review:${attempt.id}:$adjudicatedAt",
        payloadFingerprint = "manual-review|${attempt.id}|$normalizedResult|${evidence.trim()}",
        channelId = attempt.channelId,
        executionGroupIdentity = attempt.executionGroupIdentity,
        requestIdentity = attempt.requestIdentity,
        externalSettlementIdentity = externalIdentity,
        amount = attempt.amount,
        currency = attempt.currency,
        result = normalizedResult,
        resultCode = "MANUAL_ADJUDICATION",
        occurredAt = adjudicatedAt,
        firstReceivedAt = adjudicatedAt,
        lastReceivedAt = adjudicatedAt,
        verified = true,
        accepted = true,
        decision = if (normalizedResult == "SUCCESS") {
            SettlementResultDisposition.SUCCESS_ACCEPTED
        } else {
            SettlementResultDisposition.FAILURE_ACCEPTED
        },
        verdictSummary = summary,
    )
    attempt.settlementResultReceipts.add(receipt)
    attempt.resultOccurredAt = adjudicatedAt
    attempt.verdictSummary = summary
    lastReviewSummary = summary
    return if (normalizedResult == "SUCCESS") {
        attempt.status = SettlementExecutionAttemptStatus.SUCCEEDED
        attempt.finalResult = SettlementExecutionFinalResult.SUCCESS
        val formedNow = formSettledSuccess(adjudicatedAt)
        resultOutcome(attempt, SettlementResultDisposition.SUCCESS_ACCEPTED, reviewSummary = summary, settledFactFormedNow = formedNow)
    } else {
        attempt.status = SettlementExecutionAttemptStatus.FAILED
        attempt.finalResult = SettlementExecutionFinalResult.FAILED
        status = MerchantSettlementStatus.FAILED
        resultOutcome(attempt, SettlementResultDisposition.FAILURE_ACCEPTED, reviewSummary = summary)
    }
}
fun MerchantSettlement.voidBeforeExecution(
    operatorIdentity: String,
    operatorRole: String,
    reason: String,
    voidedAt: LocalDateTime,
) {
    requireAuthorized(operatorIdentity, operatorRole)
    require(reason.isNotBlank()) { "void reason must not be blank" }
    require(status in setOf(
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
    )) { "settlement $id cannot be voided while $status" }
    require(settlementExecutionAttempts.isEmpty()) { "settlement $id cannot be voided after external execution began" }
    status = MerchantSettlementStatus.VOIDED
    this.voidedBy = operatorIdentity.trim()
    this.voidReason = reason.trim()
    this.voidedAt = voidedAt
    effectiveScopeIdentity = null
    settlementLines.forEach { it.effectiveConsumptionIdentity = null }
}

fun MerchantSettlement.returnForAdjustment(
    operatorIdentity: String,
    operatorRole: String,
    reason: String,
    returnedAt: LocalDateTime,
) {
    require(!compositionFrozen) { "confirmed settlement $id cannot be returned for adjustment" }
    require(reason.isNotBlank()) { "adjustment return reason must not be blank" }
    voidBeforeExecution(
        operatorIdentity = operatorIdentity,
        operatorRole = operatorRole,
        reason = "RETURN_FOR_ADJUSTMENT: ${reason.trim()}",
        voidedAt = returnedAt,
    )
}

fun MerchantSettlement.activateEffectiveOwnership() {
    require(status in setOf(
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
    )) { "settlement $id cannot activate effective ownership while $status" }
    require(settlementLines.isNotEmpty()) { "settlement $id has no settlement lines to activate" }
    require(effectiveScopeIdentity == null || effectiveScopeIdentity == scopeIdentity) {
        "settlement $id effective scope identity cannot change"
    }
    settlementLines.forEach { line ->
        val expected = stableIdentity("ACTIVE", line.sourceKind.name, line.sourceFactIdentity)
        require(line.effectiveConsumptionIdentity == null || line.effectiveConsumptionIdentity == expected) {
            "settlement line ${line.id} effective consumption identity cannot change"
        }
    }
    effectiveScopeIdentity = scopeIdentity
    settlementLines.forEach { line ->
        line.effectiveConsumptionIdentity = stableIdentity("ACTIVE", line.sourceKind.name, line.sourceFactIdentity)
    }
}

fun MerchantSettlement.requestActivation() {
    DomainEventSupervisor.instance.attach(
        MerchantSettlementActivationRequestedDomainEvent(id.toString()),
        this,
    )
}

fun MerchantSettlement.linkReplacement(replacementId: MerchantSettlementId) {
    require(status == MerchantSettlementStatus.VOIDED) { "only a voided settlement may link a replacement" }
    replacementSettlementId = replacementId.toString()
}

fun MerchantSettlement.linkPredecessor(predecessorId: MerchantSettlementId) {
    require(status in setOf(
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
    )) { "only an unconfirmed settlement may link a predecessor" }
    require(predecessorSettlementId == null || predecessorSettlementId == predecessorId.toString()) {
        "settlement $id predecessor cannot change"
    }
    predecessorSettlementId = predecessorId.toString()
}

private fun MerchantSettlement.formSettledSuccess(completedAt: LocalDateTime): Boolean {
    status = MerchantSettlementStatus.SUCCEEDED
    this.completedAt = completedAt
    if (settledFactFormed) return false

    settledFactFormed = true
    DomainEventSupervisor.instance.attach(
        MerchantSettlementCompletedDomainEvent(
            eventIdentity = stableIdentity("MerchantSettlementCompleted:v1", id.toString()),
            settlementId = id.toString(),
            merchantId = merchantId,
            channelId = channelId,
            currency = currency,
            netAmount = netAmount,
            completedAt = completedAt,
        ),
        this,
    )
    return true
}

private fun MerchantSettlement.requireAuthorized(operatorIdentity: String, operatorRole: String) {
    require(operatorIdentity.isNotBlank()) { "operatorIdentity must not be blank" }
    require(operatorRole.trim().uppercase() == SETTLEMENT_OPERATOR_ROLE) {
        "operator role is not authorized for merchant settlement"
    }
}

private fun MerchantSettlement.requireAttempt(attemptId: SettlementExecutionAttemptId): SettlementExecutionAttempt =
    settlementExecutionAttempts.firstOrNull { it.id == attemptId }
        ?: error("settlement attempt $attemptId does not belong to settlement $id")

private fun MerchantSettlement.requireTotalsMatchLines() {
    require(settlementLines.all { it.currency == currency }) { "settlement lines must use settlement currency $currency" }
    require(settlementLines.map { it.lineIdentity }.distinct().size == settlementLines.size) {
        "settlement line identities must be unique"
    }
    require(settlementLines.map { it.sourceKind to it.sourceFactIdentity }.distinct().size == settlementLines.size) {
        "settlement source facts must be unique"
    }
    val calculatedPaymentGross = settlementLines.filter { it.transactionKind.name == "PAYMENT" }
        .fold(BigDecimal.ZERO) { total, line -> total + line.grossAmount }
    val calculatedRefundGross = settlementLines.filter { it.transactionKind.name == "REFUND" }
        .fold(BigDecimal.ZERO) { total, line -> total + line.grossAmount }
    val calculatedFees = settlementLines.fold(BigDecimal.ZERO) { total, line -> total + line.feeAmount }
    val calculatedAdjustments = settlementLines.filter { it.sourceKind.name == "ADJUSTMENT" }
        .fold(BigDecimal.ZERO) { total, line -> total + line.signedNetAmount }
    val calculatedNet = settlementLines.fold(BigDecimal.ZERO) { total, line -> total + line.signedNetAmount }
    require(paymentGrossAmount.compareTo(calculatedPaymentGross) == 0) { "payment gross total does not match settlement lines" }
    require(refundGrossAmount.compareTo(calculatedRefundGross) == 0) { "refund gross total does not match settlement lines" }
    require(feeTotalAmount.compareTo(calculatedFees) == 0) { "fee total does not match settlement lines" }
    require(adjustmentTotalAmount.compareTo(calculatedAdjustments) == 0) { "adjustment total does not match settlement lines" }
    require(netAmount.compareTo(calculatedNet) == 0) { "net amount does not match settlement lines" }
}

private fun MerchantSettlement.markResultConflict(
    attempt: SettlementExecutionAttempt,
    receipt: SettlementResultReceipt,
    summary: String,
): SettlementResultRecordingOutcome {
    receipt.accepted = false
    receipt.decision = SettlementResultDisposition.CONFLICT
    receipt.conflictSummary = summary
    attempt.conflictingNotificationCount += 1
    attempt.conflictSummary = summary
    attempt.status = SettlementExecutionAttemptStatus.CONFLICT_REVIEW_REQUIRED
    lastConflictSummary = summary
    if (!settledFactFormed) status = MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED
    return resultOutcome(attempt, SettlementResultDisposition.CONFLICT, conflictSummary = summary)
}

private fun SettlementExecutionFinalResult.matches(result: String): Boolean = when (this) {
    SettlementExecutionFinalResult.SUCCESS -> result == "SUCCESS"
    SettlementExecutionFinalResult.FAILED, SettlementExecutionFinalResult.GATEWAY_REJECTED -> result == "FAILED"
    SettlementExecutionFinalResult.UNKNOWN -> result == "UNKNOWN"
}

private fun MerchantSettlement.resultOutcome(
    attempt: SettlementExecutionAttempt?,
    disposition: SettlementResultDisposition,
    rejectionSummary: String? = null,
    conflictSummary: String? = null,
    reviewSummary: String? = null,
    settledFactFormedNow: Boolean = false,
): SettlementResultRecordingOutcome = SettlementResultRecordingOutcome(
    settlementStatus = status,
    attemptStatus = attempt?.status,
    notificationReceiveCount = attempt?.notificationReceiveCount ?: 0,
    disposition = disposition,
    rejectionSummary = rejectionSummary,
    conflictSummary = conflictSummary,
    reviewSummary = reviewSummary,
    settledFactFormedNow = settledFactFormedNow,
)
