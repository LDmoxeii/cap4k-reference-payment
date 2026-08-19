package com.only4.cap4k.reference.payment.domain.aggregates.refund.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus

@DesignBlockMetadata(
    tag = "value_object",
    name = "RefundResultRecordingOutcome",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.values",
    description = "Transient domain outcome returned after recording and adjudicating a refund channel result",
    aggregates = ["Refund"],
    family = "value-object"
)
data class RefundResultRecordingOutcome(
    val refundStatus: RefundStatus,
    val attemptStatus: RefundAttemptStatus?,
    val notificationReceiveCount: Int,
    val disposition: RefundResultDisposition,
    val reservationReleasedNow: Boolean,
    val reservationConvertedToSuccessNow: Boolean,
    val reviewRequiredNow: Boolean,
    val rejectionSummary: String?,
    val conflictSummary: String?
) {
    init {
        require(notificationReceiveCount > 0) { "notificationReceiveCount must be positive" }
        require(!(reservationReleasedNow && reservationConvertedToSuccessNow)) {
            "a refund result cannot release and convert the same reservation"
        }
        require((attemptStatus == null) == (disposition == RefundResultDisposition.ATTEMPT_NOT_FOUND)) {
            "only ATTEMPT_NOT_FOUND may omit the attempt status"
        }
        require(!reviewRequiredNow || refundStatus == RefundStatus.REVIEW_REQUIRED) {
            "reviewRequiredNow requires REVIEW_REQUIRED refund status"
        }
        require(!reservationReleasedNow || disposition == RefundResultDisposition.FAILURE_ACCEPTED) {
            "only an accepted failure may release a reservation"
        }
        require(!reservationConvertedToSuccessNow || disposition == RefundResultDisposition.SUCCESS_ACCEPTED) {
            "only an accepted success may convert a reservation"
        }
        require(!reservationReleasedNow || refundStatus == RefundStatus.FAILED) {
            "a released reservation requires FAILED refund status"
        }
        require(!reservationConvertedToSuccessNow || refundStatus == RefundStatus.SUCCEEDED) {
            "a converted reservation requires SUCCEEDED refund status"
        }
        when (disposition) {
            RefundResultDisposition.REJECTED,
            RefundResultDisposition.REJECTED_DUPLICATE,
            RefundResultDisposition.ATTEMPT_NOT_FOUND ->
                require(!rejectionSummary.isNullOrBlank()) {
                    "rejected refund outcomes require a rejection summary"
                }
            RefundResultDisposition.CONFLICT ->
                require(!conflictSummary.isNullOrBlank()) {
                    "conflicting refund outcomes require a conflict summary"
                }
            else -> Unit
        }
    }

    val duplicate: Boolean
        get() = disposition == RefundResultDisposition.ACCEPTED_DUPLICATE ||
            disposition == RefundResultDisposition.REJECTED_DUPLICATE

    val accepted: Boolean
        get() = disposition == RefundResultDisposition.SUCCESS_ACCEPTED ||
            disposition == RefundResultDisposition.FAILURE_ACCEPTED ||
            disposition == RefundResultDisposition.UNKNOWN_ACCEPTED ||
            disposition == RefundResultDisposition.ACCEPTED_DUPLICATE

    val rejected: Boolean
        get() = disposition == RefundResultDisposition.REJECTED ||
            disposition == RefundResultDisposition.REJECTED_DUPLICATE ||
            disposition == RefundResultDisposition.ATTEMPT_NOT_FOUND

    val conflicting: Boolean
        get() = disposition == RefundResultDisposition.CONFLICT
}
