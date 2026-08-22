package com.only4.cap4k.reference.payment.domain.aggregates.payment.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.*

@DesignBlockMetadata(tag = "value_object", name = "ChannelResultRecordingOutcome", packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.values", description = "Transient domain outcome returned after recording and adjudicating a channel result", aggregates = ["Payment"], family = "value-object")
data class ChannelResultRecordingOutcome(
    val paymentStatus: PaymentStatus,
    val attemptStatus: PaymentAttemptStatus?,
    val notificationReceiveCount: Int,
    val disposition: ChannelResultDisposition,
    val rejectionSummary: String?,
    val conflictSummary: String?,
    val successFactFormedNow: Boolean,
    val reviewIdentity: String? = null,
    val settlementEligible: Boolean = true,
    val notificationIntentState: PaymentNotificationIntentState? = null,
) {
    val duplicate get() = disposition.isDuplicate()
    val accepted get() = disposition.isAccepted()
    val rejected get() = disposition.isRejected()
    val conflicting get() = disposition.isConflicting()
    init {
        require(notificationReceiveCount >= 1)
        require(disposition.isTerminal())
        require((disposition == ChannelResultDisposition.ATTEMPT_NOT_FOUND) == (attemptStatus == null))
        require(rejected == !rejectionSummary.isNullOrBlank())
        require(conflicting == !conflictSummary.isNullOrBlank())
        require(!successFactFormedNow || disposition == ChannelResultDisposition.SUCCESS_ACCEPTED)
        require(!duplicate || !successFactFormedNow)
        require(disposition != ChannelResultDisposition.SUCCESS_ACCEPTED || (paymentStatus == PaymentStatus.SUCCEEDED && attemptStatus == PaymentAttemptStatus.SUCCEEDED))
        require(disposition != ChannelResultDisposition.FAILURE_ACCEPTED || attemptStatus == PaymentAttemptStatus.FAILED)
    }
}
