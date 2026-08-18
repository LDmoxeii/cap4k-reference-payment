package com.only4.cap4k.reference.payment.domain.aggregates.payment.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus

@DesignBlockMetadata(
    tag = "value_object",
    name = "ChannelResultRecordingOutcome",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.values",
    description = "Transient domain outcome returned after recording and adjudicating a channel result",
    aggregates = ["Payment"],
    family = "value-object"
)
data class ChannelResultRecordingOutcome(
    val paymentStatus: PaymentStatus,
    val attemptStatus: PaymentAttemptStatus?,
    val notificationReceiveCount: Int,
    val disposition: ChannelResultDisposition,
    val rejectionSummary: String?,
    val conflictSummary: String?,
    val successFactFormedNow: Boolean
) {
    val duplicate: Boolean
        get() = disposition.isDuplicate()

    val accepted: Boolean
        get() = disposition.isAccepted()

    val rejected: Boolean
        get() = disposition.isRejected()

    val conflicting: Boolean
        get() = disposition.isConflicting()

    init {
        require(notificationReceiveCount >= 1) { "notificationReceiveCount must be at least 1" }
        require(disposition.isTerminal()) { "RECEIVED is an intermediate receipt state, not a final outcome" }
        require((disposition == ChannelResultDisposition.ATTEMPT_NOT_FOUND) == (attemptStatus == null)) {
            "attemptStatus must be null only for ATTEMPT_NOT_FOUND"
        }
        require(rejected == !rejectionSummary.isNullOrBlank()) {
            "rejectionSummary must be present exactly for rejected outcomes"
        }
        require(conflicting == !conflictSummary.isNullOrBlank()) {
            "conflictSummary must be present exactly for conflicting outcomes"
        }
        require(!successFactFormedNow || disposition == ChannelResultDisposition.SUCCESS_ACCEPTED) {
            "only SUCCESS_ACCEPTED can form the success fact"
        }
        require(!duplicate || !successFactFormedNow) { "duplicate outcomes cannot form the success fact again" }
        require(
            disposition != ChannelResultDisposition.SUCCESS_ACCEPTED ||
                (paymentStatus == PaymentStatus.SUCCEEDED && attemptStatus == PaymentAttemptStatus.SUCCEEDED)
        ) { "SUCCESS_ACCEPTED requires succeeded payment and attempt states" }
        require(disposition != ChannelResultDisposition.FAILURE_ACCEPTED || attemptStatus == PaymentAttemptStatus.FAILED) {
            "FAILURE_ACCEPTED requires a failed attempt state"
        }
    }
}
