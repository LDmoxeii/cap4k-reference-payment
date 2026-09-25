package com.only4.cap4k.reference.payment.domain.aggregates.payment.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.*

@DesignBlockMetadata(tag = "value_object", name = "ChannelResultRecordingOutcome", packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.values", description = "Transient domain outcome returned after recording and adjudicating a channel result", aggregates = ["Payment"], family = "value-object")
data class ChannelResultRecordingOutcome(
    /**
     * 支付状态
     */
    val paymentStatus: PaymentStatus,
    /**
     * 尝试状态
     */
    val attemptStatus: PaymentAttemptStatus?,
    /**
     * 通知接收次数
     */
    val notificationReceiveCount: Int,
    /**
     * 处置结果
     */
    val disposition: ChannelResultDisposition,
    /**
     * 拒绝摘要
     */
    val rejectionSummary: String?,
    /**
     * 冲突摘要
     */
    val conflictSummary: String?,
    /**
     * 当前是否形成成功事实
     */
    val successFactFormedNow: Boolean,
    /**
     * 复核身份
     */
    val reviewIdentity: String? = null,
    /**
     * 是否符合结算条件
     */
    val settlementEligible: Boolean = true,
    /**
     * 通知意图状态
     */
    val notificationIntentState: PaymentNotificationIntentState? = null,
) {
    val duplicate get() = disposition.isDuplicate()
    val accepted get() = disposition.isAccepted()
    val rejected get() = disposition.isRejected()
    val conflicting get() = disposition.isConflicting()
    init {
        require(notificationReceiveCount >= 1)
        require(disposition.isTerminal())
        require(disposition != ChannelResultDisposition.ATTEMPT_NOT_FOUND || attemptStatus == null)
        require(
            attemptStatus != null || disposition in setOf(
                ChannelResultDisposition.ATTEMPT_NOT_FOUND,
                ChannelResultDisposition.ACCEPTED_DUPLICATE,
                ChannelResultDisposition.REJECTED_DUPLICATE,
            ),
        )
        require(rejected == !rejectionSummary.isNullOrBlank())
        require(conflicting == !conflictSummary.isNullOrBlank())
        require(!successFactFormedNow || disposition == ChannelResultDisposition.SUCCESS_ACCEPTED)
        require(!duplicate || !successFactFormedNow)
        require(disposition != ChannelResultDisposition.SUCCESS_ACCEPTED || (paymentStatus == PaymentStatus.SUCCEEDED && attemptStatus == PaymentAttemptStatus.SUCCEEDED))
        require(disposition != ChannelResultDisposition.FAILURE_ACCEPTED || attemptStatus == PaymentAttemptStatus.FAILED)
        require(disposition != ChannelResultDisposition.UNKNOWN_ACCEPTED || (paymentStatus == PaymentStatus.RESULT_UNKNOWN && attemptStatus == PaymentAttemptStatus.RESULT_UNKNOWN))
    }
}
