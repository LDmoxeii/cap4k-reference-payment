package com.only4.cap4k.reference.payment.domain.aggregates.refund

import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.values.RefundResultRecordingOutcome
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 创建或复用退款渠道尝试。PROCESSING attempt 是应用重试的幂等边界；
 * RESULT_UNKNOWN/REVIEW_REQUIRED 不会释放 Payment 预算，只有明确失败才允许重新发起。
 */
fun Refund.startAttempt(
    now: LocalDateTime,
    channelId: String,
    configurationId: String,
    snapshot: String,
    requestIdentity: String,
    reviewAfterAt: LocalDateTime,
): RefundAttempt {
    require(status == RefundStatus.PROCESSING || status == RefundStatus.RESULT_UNKNOWN || status == RefundStatus.REVIEW_REQUIRED) {
        "退款单 $id 当前状态为 $status，不能发起渠道退款"
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
        ?: error("退款尝试 $attemptId 不属于退款单 $id")
    require(attempt.status == RefundAttemptStatus.PROCESSING) {
        "退款尝试 $attemptId 当前状态为 ${attempt.status}，不能标记为渠道已受理"
    }
    attempt.acceptedAt = acceptedAt
    attempt.channelRefundId = channelRefundId
    this.channelRefundId = channelRefundId
    channelAcceptedAt = acceptedAt
}

/** 渠道请求明确拒绝时终结 attempt 并释放 Payment 侧预留预算；未知异常不能走此分支。 */
fun Refund.rejectAttemptStart(
    attemptId: RefundAttemptId,
    failureCode: String,
    diagnosticSummary: String?,
) {
    val attempt = attempts.firstOrNull { it.id == attemptId }
        ?: error("退款尝试 $attemptId 不属于退款单 $id")
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

/**
 * 追加并裁决退款渠道结果。notification identity 负责去重，同 identity 异 payload 或终态后的相反结果
 * 只形成冲突证据；成功把 reserved 原子转换为 successful，失败释放 reserved，未知继续占用并等待复核。
 */
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
        "不支持的渠道退款结果：$result"
    }
    val normalizedCurrency = currency.trim().uppercase()
    notificationReceiveCount += 1
    lastNotificationIdentity = notificationId
    lastNotificationReceivedAt = receivedAt

    val attempt = attempts.firstOrNull { it.id == attemptId }
        ?: run {
            val rejection = "退款尝试 $attemptId 不属于退款单 $id"
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
            return markConflict(attempt, existing, "通知 $notificationId 被重复使用，但 payload 与首次接收内容冲突")
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
        return markConflict(attempt, receipt, "通知 $notificationId 与已终结的退款尝试 ${attempt.id} 冲突")
    }

    val rejection = when {
        !verified -> verificationSummary ?: "渠道退款结果核验失败"
        attempt.channelId != channelId -> "渠道 $channelId 与退款尝试渠道 ${attempt.channelId} 不一致"
        this.amount.compareTo(amount) != 0 -> "通知金额 $amount 与退款金额 ${this.amount} 不一致"
        this.currency != normalizedCurrency -> "通知币种 $normalizedCurrency 与退款币种 ${this.currency} 不一致"
        attempt.channelRefundId != null && attempt.channelRefundId != channelRefundId -> "渠道退款号与已受理的退款尝试不一致"
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
    attempt.verdictSummary = verificationSummary ?: "渠道退款结果核验通过"
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

/** 超过冻结阈值仍无最终结果时只进入 REVIEW_REQUIRED；预算继续占用，避免未知状态下重复退款。 */
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

/** 冲突 receipt 只追加审计并阻断结算，不覆盖已经形成的退款成功事实。 */
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
