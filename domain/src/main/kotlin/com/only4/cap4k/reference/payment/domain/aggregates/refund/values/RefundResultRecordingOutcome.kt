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
        require(notificationReceiveCount > 0) { "通知接收次数必须大于零" }
        require(!(reservationReleasedNow && reservationConvertedToSuccessNow)) {
            "同一退款结果不能同时释放并转成功同一笔预留预算"
        }
        require((attemptStatus == null) == (disposition == RefundResultDisposition.ATTEMPT_NOT_FOUND)) {
            "only ATTEMPT_NOT_FOUND may omit the attempt status"
        }
        require(!reviewRequiredNow || refundStatus == RefundStatus.REVIEW_REQUIRED) {
            "reviewRequiredNow requires REVIEW_REQUIRED refund status"
        }
        require(!reservationReleasedNow || disposition == RefundResultDisposition.FAILURE_ACCEPTED) {
            "只有已接受失败结果才能释放退款预留预算"
        }
        require(!reservationConvertedToSuccessNow || disposition == RefundResultDisposition.SUCCESS_ACCEPTED) {
            "只有已接受成功结果才能转换退款预留预算"
        }
        require(!reservationReleasedNow || refundStatus == RefundStatus.FAILED) {
            "释放预算时退款状态必须为 FAILED"
        }
        require(!reservationConvertedToSuccessNow || refundStatus == RefundStatus.SUCCEEDED) {
            "预算转成功时退款状态必须为 SUCCEEDED"
        }
        when (disposition) {
            RefundResultDisposition.REJECTED,
            RefundResultDisposition.REJECTED_DUPLICATE,
            RefundResultDisposition.ATTEMPT_NOT_FOUND ->
                require(!rejectionSummary.isNullOrBlank()) {
                    "被拒绝的退款结果必须包含拒绝摘要"
                }
            RefundResultDisposition.CONFLICT ->
                require(!conflictSummary.isNullOrBlank()) {
                    "冲突退款结果必须包含冲突摘要"
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
