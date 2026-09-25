package com.only4.cap4k.reference.payment.domain.aggregates.operation

import com.only4.cap4k.reference.payment.domain.aggregates.operation.factory.OperationFactory
import java.time.LocalDateTime

/** The initial state is either a completed synchronous READ_ONCE command or an accepted POLL command. */
fun OperationFactory.Payload.requireInitialOperationState() {
    require(
        (status == "SUCCEEDED" && finality == "FINAL" && readAfterMode == "READ_ONCE" &&
            !readAfterResourceUrl.isNullOrBlank() && retryAfterMs == 0L && completedAt != null) ||
            (status == "ACCEPTED" && finality == "NON_FINAL" && readAfterMode == "POLL" &&
                retryAfterMs > 0L && completedAt == null)
    ) { "INVALID_STATE_TRANSITION: initial Operation status, finality and readAfter must agree" }
}

fun Operation.markProcessing(at: LocalDateTime) {
    if (status != "ACCEPTED") invalidTransition("Operation cannot enter PROCESSING from $status")
    status = "PROCESSING"
    updatedAt = at
}

fun Operation.succeed(at: LocalDateTime, resourceUrl: String? = readAfterResourceUrl, resultJson: String? = null) {
    requirePending()
    if (resourceUrl.isNullOrBlank()) invalidTransition("SUCCEEDED requires a readable resourceUrl")
    status = "SUCCEEDED"
    finality = "FINAL"
    readAfterResourceUrl = resourceUrl
    this.resultJson = resultJson
    completedAt = at
    updatedAt = at
}

fun Operation.fail(
    at: LocalDateTime,
    code: String,
    message: String,
    detailsJson: String = "{}",
    correlationId: String? = null,
    retryable: Boolean = false,
) {
    requirePending()
    if (code.isBlank() || message.isBlank()) invalidTransition("FAILED requires a stable error")
    status = "FAILED"
    finality = "FINAL"
    errorCode = code
    errorMessage = message
    errorDetailsJson = detailsJson
    errorCorrelationId = correlationId
    errorRetryable = retryable
    completedAt = at
    updatedAt = at
}

fun Operation.requireReview(at: LocalDateTime, reviewId: String) {
    requirePending()
    if (reviewId.isBlank()) invalidTransition("REVIEW_REQUIRED requires reviewId")
    status = "REVIEW_REQUIRED"
    finality = "REVIEW_REQUIRED"
    this.reviewId = reviewId
    completedAt = at
    updatedAt = at
}

private fun Operation.requirePending() {
    if (readAfterMode != "POLL" || status !in setOf("ACCEPTED", "PROCESSING")) {
        invalidTransition("Operation cannot converge from $status")
    }
}

class OperationStateTransitionException(message: String) : IllegalStateException("INVALID_STATE_TRANSITION: $message")

private fun invalidTransition(message: String): Nothing = throw OperationStateTransitionException(message)
