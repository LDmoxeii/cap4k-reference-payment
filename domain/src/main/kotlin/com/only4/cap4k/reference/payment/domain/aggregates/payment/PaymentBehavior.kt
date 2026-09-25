package com.only4.cap4k.reference.payment.domain.aggregates.payment

import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.*
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.PaymentExpiryOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.PaymentReviewEligibility
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime

data class SettlementFeeRule(
    val configurationId: String,
    val basisPoints: Int,
    val fixedFeeAmount: BigDecimal,
    val roundingMode: RoundingMode,
    val currencyPrecision: Int,
    /** Effective ReferencePolicy fee rate frozen with the first accepted success fact. */
    val feeRate: BigDecimal = BigDecimal.valueOf(basisPoints.toLong()).movePointLeft(4),
) {
    init {
        require(configurationId.isNotBlank())
        require(feeRate >= BigDecimal.ZERO)
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

/** 支付复核的稳定机器错误码与中文展示消息，避免 application 再从 Throwable.message 反解析 code。 */
class PaymentReviewException(
    val code: String,
    message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)

private fun reviewFailure(
    code: String,
    message: String,
    details: Map<String, String> = emptyMap(),
): Nothing = throw PaymentReviewException(code, message, details)

private fun reviewCheck(
    condition: Boolean,
    code: String,
    message: String,
    details: Map<String, String> = emptyMap(),
) {
    if (!condition) reviewFailure(code, message, details)
}

/**
 * Creates an explicit payment attempt only.  This is deliberately separated from submission:
 * no channel call or channel receipt can happen on this transition.
 */
fun Payment.createAttempt(
    channelId: String,
    channelConfigurationId: String,
    channelConfigurationSnapshot: String,
    requestIdentity: String,
    initiatedAt: LocalDateTime,
    interactionInformation: String,
    riskReason: String? = null,
): PaymentAttempt {
    check(initiatedAt.isBefore(expiresAt)) { "PAYMENT_EXPIRED" }
    check(status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING) {
        "支付 $id 当前状态为 $status，不能创建新的支付尝试"
    }
    val inFlight = attempts.filter { it.status in inFlightAttemptStatuses }
    if (inFlight.isNotEmpty()) {
        check(!riskReason.isNullOrBlank()) { "PAYMENT_ATTEMPT_RISK_REASON_REQUIRED" }
        openReview(
            PaymentReviewType.CONCURRENT_ATTEMPT_RISK,
            initiatedAt,
            inFlight.map { it.id.toString() }.sorted(),
            emptyList(),
            "存在未收敛支付尝试，新尝试必须按显式风险原因创建：${riskReason.trim()}",
            "concurrent-attempt:${inFlight.map { it.id.toString() }.sorted().joinToString(",")}:${riskReason.trim()}",
        )
    } else {
        check(currentReviewEligibility().settlementEligible) { "PAYMENT_REVIEW_REQUIRED" }
    }
    return PaymentAttempt(
        channelId = channelId,
        channelConfigurationId = channelConfigurationId,
        channelConfigurationSnapshot = channelConfigurationSnapshot,
        requestIdentity = requestIdentity,
        status = PaymentAttemptStatus.CREATED,
        initiatedAt = initiatedAt,
        interactionInformation = interactionInformation,
        riskReason = riskReason?.trim()?.takeIf { it.isNotEmpty() },
    ).also {
        attempts.add(it)
        attemptCount = attempts.size
    }
}

/**
 * Freezes an unchangeable submission identity before the adapter invokes the reference channel.
 * Any later invocation must observe a non-CREATED state and is therefore unable to call again.
 */
fun Payment.freezeAttemptSubmission(paymentAttemptId: PaymentAttemptId, submittedAt: LocalDateTime): PaymentAttempt {
    val attempt = attempts.firstOrNull { it.id == paymentAttemptId }
        ?: error("支付尝试 $paymentAttemptId 不属于支付单 $id")
    check(attempt.status == PaymentAttemptStatus.CREATED) {
        "支付尝试 $paymentAttemptId 当前状态为 ${attempt.status}，不能提交给渠道"
    }
    attempt.submissionIdentity = "payment-submit:${attempt.id}"
    attempt.submittedAt = submittedAt
    attempt.status = PaymentAttemptStatus.SUBMITTED
    status = PaymentStatus.PROCESSING
    return attempt
}

/** Appends the sole channel submission receipt and advances only submission acceptance, never payment success. */
fun Payment.recordAttemptSubmission(
    paymentAttemptId: PaymentAttemptId,
    submissionIdentity: String,
    submittedAt: LocalDateTime,
    outcome: String,
    channelReference: String?,
    diagnosticSummary: String?,
): PaymentAttempt {
    val attempt = attempts.firstOrNull { it.id == paymentAttemptId }
        ?: error("支付尝试 $paymentAttemptId 不属于支付单 $id")
    check(attempt.submissionIdentity == submissionIdentity) { "PAYMENT_SUBMISSION_IDENTITY_CONFLICT" }
    val normalizedOutcome = outcome.trim().uppercase()
    check(normalizedOutcome in setOf("ACCEPTED", "REJECTED", "RESULT_UNKNOWN")) {
        "不支持的支付渠道提交结果：$outcome"
    }
    check(attempt.status == PaymentAttemptStatus.SUBMITTED) {
        "支付尝试 $paymentAttemptId 当前状态为 ${attempt.status}，不能记录渠道提交结果"
    }
    check(attempt.paymentSubmissionReceipts.none { it.submissionIdentity == submissionIdentity }) {
        "PAYMENT_SUBMISSION_IDENTITY_CONFLICT"
    }
    attempt.paymentSubmissionReceipts.add(
        PaymentSubmissionReceipt(
            submissionIdentity = submissionIdentity,
            requestIdentity = attempt.requestIdentity,
            channelId = attempt.channelId,
            submittedAt = submittedAt,
            outcome = normalizedOutcome,
            channelReference = channelReference?.trim()?.takeIf { it.isNotEmpty() },
            diagnosticSummary = diagnosticSummary?.trim()?.takeIf { it.isNotEmpty() },
        ),
    )
    when (normalizedOutcome) {
        "ACCEPTED" -> {
            attempt.status = PaymentAttemptStatus.ACCEPTED
            attempt.acceptedAt = submittedAt
            if (!channelReference.isNullOrBlank()) attempt.interactionInformation = channelReference.trim()
            status = PaymentStatus.PROCESSING
        }
        "REJECTED" -> {
            attempt.status = PaymentAttemptStatus.REJECTED
            attempt.finalResult = PaymentAttemptFinalResult.GATEWAY_REJECTED
            attempt.completedAt = submittedAt
            attempt.rejectionSummary = diagnosticSummary ?: "支付渠道拒绝提交"
            rejectedNotificationCount += 1
            lastRejectionSummary = attempt.rejectionSummary
            status = when {
                attempts.any { it.status in inFlightAttemptStatuses } -> PaymentStatus.PROCESSING
                submittedAt.isBefore(expiresAt) -> PaymentStatus.PENDING
                else -> PaymentStatus.FAILED
            }
        }
        else -> {
            attempt.status = PaymentAttemptStatus.RESULT_UNKNOWN
            attempt.finalResult = PaymentAttemptFinalResult.RESULT_UNKNOWN
            attempt.completedAt = submittedAt
            attempt.rejectionSummary = diagnosticSummary ?: "支付渠道提交结果未知"
            status = PaymentStatus.RESULT_UNKNOWN
        }
    }
    return attempt
}

/**
 * Retained only as an in-process domain-test helper while legacy tests migrate.  It is not an
 * application command or HTTP surface and never calls a channel.
 */
@Deprecated("Use createAttempt followed by freezeAttemptSubmission")
fun Payment.startAttempt(
    channelId: String,
    channelConfigurationId: String,
    channelConfigurationSnapshot: String,
    requestIdentity: String,
    initiatedAt: LocalDateTime,
): PaymentAttempt = createAttempt(
    channelId = channelId,
    channelConfigurationId = channelConfigurationId,
    channelConfigurationSnapshot = channelConfigurationSnapshot,
    requestIdentity = requestIdentity,
    initiatedAt = initiatedAt,
    interactionInformation = "legacy:$channelId",
).also { attempt ->
    attempt.submissionIdentity = "legacy:${attempt.requestIdentity}"
    attempt.submittedAt = initiatedAt
    attempt.status = PaymentAttemptStatus.SUBMITTED
    status = PaymentStatus.PROCESSING
}

fun Payment.rejectAttemptStart(paymentAttemptId: PaymentAttemptId, failureCode: String, diagnosticSummary: String?) {
    val attempt = attempts.firstOrNull { it.id == paymentAttemptId }
        ?: error("支付尝试 $paymentAttemptId 不属于支付单 $id")
    attempt.status = PaymentAttemptStatus.REJECTED
    attempt.finalResult = PaymentAttemptFinalResult.GATEWAY_REJECTED
    attempt.completedAt = attempt.submittedAt ?: attempt.initiatedAt
    attempt.rejectionSummary = listOfNotNull(failureCode, diagnosticSummary).joinToString(": ")
    rejectedNotificationCount += 1
    lastRejectionSummary = attempt.rejectionSummary
    if (status != PaymentStatus.SUCCEEDED) {
        status = if (attempt.completedAt!!.isBefore(expiresAt)) PaymentStatus.PENDING else PaymentStatus.FAILED
    }
}

/**
 * 根据业务到期时间收敛支付状态。
 *
     * 没有在途 attempt 时可以安全关闭；存在 SUBMITTED/ACCEPTED/RESULT_UNKNOWN attempt 时必须进入
 * RESULT_UNKNOWN 并形成稳定 review，避免把渠道可能已成功的交易误判为失败或再次付款。
 */
fun Payment.expire(
    now: LocalDateTime,
    unknownResultReviewAfter: Duration = Duration.ZERO,
): PaymentExpiryOutcome {
    require(!unknownResultReviewAfter.isNegative) { "支付未知结果复核期限不能为负数" }
    if (status in setOf(PaymentStatus.CLOSED, PaymentStatus.FAILED, PaymentStatus.SUCCEEDED)) {
        return PaymentExpiryOutcome(status, false, false, null)
    }
    val pending = attempts.filter {
            it.status in inFlightAttemptStatuses
    }
    if (pending.isEmpty()) {
        if (now.isBefore(expiresAt)) return PaymentExpiryOutcome(status, false, false, null)
        status = PaymentStatus.CLOSED
        closedAt = now
        closeReason = "PAYMENT_EXPIRED_WITHOUT_PENDING_ATTEMPT"
        currentReviewEligibility()
        return PaymentExpiryOutcome(status, true, false, null)
    }
    if (status != PaymentStatus.RESULT_UNKNOWN && now.isBefore(expiresAt)) {
        return PaymentExpiryOutcome(status, false, false, null)
    }
    pending.forEach {
        if (it.status != PaymentAttemptStatus.RESULT_UNKNOWN) {
            it.status = PaymentAttemptStatus.RESULT_UNKNOWN
            if (it.finalResult == null) it.finalResult = PaymentAttemptFinalResult.RESULT_UNKNOWN
            it.completedAt = now
        }
    }
    status = PaymentStatus.RESULT_UNKNOWN
    val reviewDueAt = pending.minOf { attempt ->
        (attempt.notificationLastReceivedAt ?: expiresAt).plusSeconds(unknownResultReviewAfter.seconds)
    }
    if (now.isBefore(reviewDueAt)) {
        currentReviewEligibility()
        return PaymentExpiryOutcome(status, false, false, null)
    }
    val ids = pending.map { it.id.toString() }.sorted()
    val (review, opened) = openReview(
        PaymentReviewType.EXPIRY_RESULT_UNKNOWN,
        now,
        ids,
        emptyList(),
        "支付已到期，但仍有待确认的渠道尝试：${ids.joinToString(",")}",
        "expiry:$expiresAt:${ids.joinToString(",")}",
    )
    return PaymentExpiryOutcome(status, false, opened, review.reviewIdentity)
}

/**
 * 从全部 OPEN review 动态派生当前结算资格。
 *
 * settlementBlocked 只是便于查询的摘要，真正依据始终是 append-only review/decision evidence，
 * 因此对账或结算不能通过修改一个布尔字段绕过未解决复核。
 */
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
        blockingReviewSummaries = blocking.map { "${it.type.name}：${it.summary}（阻断结算）" },
    )
}

/**
 * 记录并裁决一次渠道结果：先按 notification/payload 去重，再验证归属与金额币种，
 * 最后根据 Payment 当前状态路由到首次结果、未知收敛、终态迟到或成功后冲突分支。
 *
 * 所有 receipt 都追加保留；重复、拒绝和冲突不会覆盖旧证据。可信成功也只能形成一次
 * 收入事实、手续费快照和商户通知意图，其他成功证据必须进入 review。
 */
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
        "不支持的渠道支付结果：$result"
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
        val summary = "支付尝试 $paymentAttemptId 不属于支付单 $id"
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
        val summary = "通知 $notificationId 被重复使用，但 payload 与首次接收内容冲突"
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
        !verified -> verificationSummary ?: "渠道结果核验失败"
        attempt.channelId != channelId -> "渠道 $channelId 与支付尝试渠道 ${attempt.channelId} 不一致"
        this.amount.compareTo(amount) != 0 -> "通知金额 $amount 与支付金额 ${this.amount} 不一致"
        this.currency != normalizedCurrency -> "通知币种 $normalizedCurrency 与支付币种 ${this.currency} 不一致"
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
    attempt.verdictSummary = verificationSummary ?: "渠道结果核验通过"
    receipt.verified = true

    if (normalizedResult == "SUCCESS" && !merchantOrderSuccessAvailable && !successFactFormed) {
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.CONFLICT
        receipt.conflictSummary = "该商户订单已经存在已接受的支付成功事实"
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
            attempt, receipt, normalizedResult, channelTransactionId, occurredAt, receivedAt, settlementFeeRule
        )
        else -> initialResult(
            attempt, receipt, normalizedResult, channelTransactionId, occurredAt, receivedAt, settlementFeeRule
        )
    }
}

/** 首次可信结果可以形成成功或失败终态；UNKNOWN 只形成待复核证据，不能伪装成失败。 */
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
        attempt.completedAt = occurredAt
        status = statusAfterAttemptFailure(receivedAt)
        outcome(attempt, ChannelResultDisposition.FAILURE_ACCEPTED)
    }
    else -> {
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.UNKNOWN_ACCEPTED
        attempt.channelTransactionId = channelTransactionId
        attempt.resultOccurredAt = occurredAt
        attempt.finalResult = PaymentAttemptFinalResult.RESULT_UNKNOWN
        attempt.status = PaymentAttemptStatus.RESULT_UNKNOWN
        attempt.completedAt = occurredAt
        status = PaymentStatus.RESULT_UNKNOWN
        outcome(attempt, ChannelResultDisposition.UNKNOWN_ACCEPTED)
    }
}

/** RESULT_UNKNOWN 只接受可信最终结果收敛，并以 SYSTEM decision 关闭对应未知复核。 */
private fun Payment.afterUnknown(
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
        attempt.completedAt = occurredAt
        status = statusAfterAttemptFailure(receivedAt)
        resolveUnknownReviews(attempt, receipt, PaymentReviewDecisionType.SYSTEM_CONFIRM_FAILURE, occurredAt)
        outcome(attempt, ChannelResultDisposition.FAILURE_ACCEPTED)
    }
    else -> {
        receipt.accepted = true
        receipt.decision = ChannelResultDisposition.UNKNOWN_ACCEPTED
        outcome(attempt, ChannelResultDisposition.UNKNOWN_ACCEPTED)
    }
}

/**
 * CLOSED/FAILED 后的结果只能追加为冲突证据；可信迟到成功必须等待授权裁决，
 * 不能直接覆盖既有终态或绕过商户订单唯一成功约束。
 */
private fun Payment.afterTerminalWithoutSuccess(
    attempt: PaymentAttempt,
    receipt: PaymentNotificationReceipt,
    result: String,
    channelTransactionId: String,
    occurredAt: LocalDateTime,
    receivedAt: LocalDateTime,
): ChannelResultRecordingOutcome {
    if (result != "SUCCESS") {
        val summary = "支付已处于终态 $status，之后又收到 ${result.lowercase()} 结果"
        receipt.decision = ChannelResultDisposition.CONFLICT
        receipt.conflictSummary = summary
        markConflict(attempt, summary)
        return outcome(attempt, ChannelResultDisposition.CONFLICT, conflict = summary)
    }
    val terminalStatus = status
    receipt.accepted = true
    receipt.decision = ChannelResultDisposition.LATE
    receipt.conflictSummary = "支付已处于终态 $terminalStatus，之后收到可信成功结果"
    attempt.channelTransactionId = channelTransactionId
    attempt.resultOccurredAt = occurredAt
    attempt.finalResult = PaymentAttemptFinalResult.SUCCESS
    attempt.status = PaymentAttemptStatus.SUCCEEDED
    attempt.completedAt = occurredAt
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
        ChannelResultDisposition.LATE,
        conflict = receipt.conflictSummary,
        reviewIdentity = review.reviewIdentity,
    )
}

/**
 * Payment 已成功后不允许任何结果回退成功事实。
 * 第二个成功 attempt 或后续失败/未知结果都保留真实 receipt，并打开阻断结算的冲突复核。
 */
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
            "支付已成功后，尝试 ${attempt.id} 又收到额外成功证据"
        } else {
            "多个支付尝试都包含可信成功证据：${firstSuccess.id},${attempt.id}"
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
        summary = "支付成功事实形成后又收到 ${result.lowercase()} 证据"
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

/** 首次接受成功时原子冻结成功身份、手续费快照和商户通知意图；此逻辑不得被其他路径复制。 */
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

/**
 * 追加一次授权复核决定，而不是修改或删除既有 review evidence。
 *
 * 决策先验证 authorization 与 decision identity 幂等，再按 review type 限定允许的动作；
 * 业务终态、结算资格和商户通知意图分别更新，避免把“接受证据”等同于“允许结算”。
 */
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
    require(operatorIdentity.isNotBlank()) { "操作员身份不能为空" }
    require(operatorRole.isNotBlank()) { "操作员角色不能为空" }
    require(reason.isNotBlank()) { "复核原因不能为空" }
    require(evidence.isNotBlank()) { "复核证据不能为空" }
    reviewCheck(authorized, "REVIEW_UNAUTHORIZED", "当前操作员无权裁决支付复核")
    val review = reviewCases.firstOrNull {
        it.reviewIdentity == reviewIdentity || runCatching { it.id.toString() }.getOrNull() == reviewIdentity
    } ?: reviewFailure(
        code = "REVIEW_NOT_FOUND",
        message = "未找到支付复核 $reviewIdentity",
        details = mapOf("reviewId" to reviewIdentity),
    )
    review.paymentReviewDecisions.firstOrNull { it.decisionIdentity == decisionIdentity }?.let { existing ->
        reviewCheck(
            existing.decision == decision &&
                existing.operatorIdentity == operatorIdentity &&
                existing.operatorRole == operatorRole &&
                existing.authorizationOutcome &&
                existing.reason == reason &&
                existing.evidence == evidence &&
                existing.decidedAt == decidedAt &&
                existing.eligibilityImpact == eligibilityImpact &&
                existing.remediationReference == remediationReference,
            code = "REVIEW_DECISION_IDEMPOTENCY_CONFLICT",
            message = "复核决定幂等键已绑定到不同内容",
            details = mapOf("decisionIdentity" to decisionIdentity),
        )
        return adjudicationOutcome(review)
    }
    reviewCheck(review.status == PaymentReviewStatus.OPEN, "REVIEW_DECISION_NOT_ALLOWED", "当前复核状态不允许追加决定")

    when (decision) {
        PaymentReviewDecisionType.ACCEPT_LATE_SUCCESS -> {
            reviewCheck(review.type in lateSuccessReviewTypes, "REVIEW_DECISION_NOT_ALLOWED", "该复核类型不允许接受迟到成功")
            reviewCheck(!successFactFormed && status != PaymentStatus.SUCCEEDED, "REVIEW_DECISION_NOT_ALLOWED", "支付已形成成功事实，不能再次接受成功")
            val evidencePair = attempts.asSequence()
                .flatMap { attempt -> attempt.paymentNotificationReceipts.asSequence().map { attempt to it } }
                .filter { (_, receipt) -> receipt.verified && receipt.accepted && receipt.result == "SUCCESS" }
                .maxByOrNull { (_, receipt) -> receipt.occurredAt }
                ?: reviewFailure("REVIEW_DECISION_NOT_ALLOWED", "复核中没有可接受的可信成功证据")
            acceptSuccess(
                evidencePair.first,
                evidencePair.second,
                evidencePair.second.channelTransactionId,
                evidencePair.second.occurredAt,
                settlementFeeRule ?: reviewFailure("REVIEW_DECISION_NOT_ALLOWED", "接受迟到成功时缺少手续费规则快照"),
            )
        }
        PaymentReviewDecisionType.CONFIRM_FAILURE -> {
            reviewCheck(review.type == PaymentReviewType.EXPIRY_RESULT_UNKNOWN, "REVIEW_DECISION_NOT_ALLOWED", "只有结果未知复核可以确认失败")
            reviewCheck(!successFactFormed && status == PaymentStatus.RESULT_UNKNOWN, "REVIEW_DECISION_NOT_ALLOWED", "支付当前状态不允许确认失败")
            status = PaymentStatus.FAILED
            attempts.filter { it.status == PaymentAttemptStatus.RESULT_UNKNOWN }.forEach {
                it.status = PaymentAttemptStatus.FAILED
                it.finalResult = PaymentAttemptFinalResult.FAILED
                it.resultOccurredAt = decidedAt
            }
        }
        PaymentReviewDecisionType.KEEP_CURRENT_TERMINAL -> {
            reviewCheck(review.type in terminalConflictReviewTypes, "REVIEW_DECISION_NOT_ALLOWED", "该复核类型不允许保留当前终态")
            reviewCheck(status == PaymentStatus.CLOSED || status == PaymentStatus.FAILED, "REVIEW_DECISION_NOT_ALLOWED", "支付当前状态不是可保留的关闭或失败终态")
        }
        PaymentReviewDecisionType.KEEP_ACCEPTED_SUCCESS_WITH_REMEDIATION -> {
            reviewCheck(review.type in acceptedSuccessConflictReviewTypes, "REVIEW_DECISION_NOT_ALLOWED", "该复核类型不允许保留已接受成功")
            reviewCheck(
                status == PaymentStatus.SUCCEEDED && successFactFormed && !remediationReference.isNullOrBlank(),
                "REVIEW_DECISION_NOT_ALLOWED",
                "保留已接受成功时必须存在成功事实和补救引用",
            )
            requireSettlementFeeSnapshot()
        }
        PaymentReviewDecisionType.SYSTEM_ACCEPT_SUCCESS,
        PaymentReviewDecisionType.SYSTEM_CONFIRM_FAILURE -> reviewFailure("REVIEW_DECISION_NOT_ALLOWED", "系统裁决类型不能通过人工入口提交")
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

private val inFlightAttemptStatuses = setOf(
    PaymentAttemptStatus.CREATED,
    PaymentAttemptStatus.SUBMITTED,
    PaymentAttemptStatus.ACCEPTED,
    PaymentAttemptStatus.RESULT_UNKNOWN,
)

/** A failed attempt is terminal; the Payment is terminal only when no retry window remains. */
private fun Payment.statusAfterAttemptFailure(at: LocalDateTime): PaymentStatus = when {
    attempts.any { it.status == PaymentAttemptStatus.RESULT_UNKNOWN } -> PaymentStatus.RESULT_UNKNOWN
    attempts.any { it.status in setOf(
        PaymentAttemptStatus.CREATED,
        PaymentAttemptStatus.SUBMITTED,
        PaymentAttemptStatus.ACCEPTED,
    ) } -> PaymentStatus.PROCESSING
    at.isBefore(expiresAt) -> PaymentStatus.PENDING
    else -> PaymentStatus.FAILED
}

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

/**
 * 可信渠道最终结果自动追加 SYSTEM decision 并关闭对应 RESULT_UNKNOWN review。
 * identity 由 review 与 decision 稳定派生，重复 callback 不会产生第二条系统决定。
 */
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
                    reason = "可信渠道最终结果已解决结果未知复核",
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

/** 使用稳定哈希 identity 打开 review；相同业务触发条件重复到达时复用既有 case。 */
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

/** 在首次支付成功时冻结手续费事实，后续配置变更不得重算既有交易或结算明细。 */
private fun Payment.freezeSettlementFee(rule: SettlementFeeRule, formedAt: LocalDateTime) {
    check(settlementFeeFactIdentity == null)
    val fee = amount
        .multiply(rule.feeRate)
        .add(rule.fixedFeeAmount)
        .setScale(rule.currencyPrecision, rule.roundingMode)
    settlementFeeFactIdentity = "payment:$id:settlement-fee"
    settlementFeeRate = rule.feeRate
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
            settlementFeeRate != null &&
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
        settlementEligible = status == PaymentStatus.SUCCEEDED && eligibility.settlementEligible,
        notificationIntentState = merchantSuccessNotificationIntentState,
    )
}

fun Payment.onCreate() = Unit
fun Payment.onDeleted() = Unit

val Payment.refundableAmount: BigDecimal
    get() = amount.subtract(reservedRefundAmount).subtract(successfulRefundAmount)

/**
 * 退款预算使用 reserved 与 successful 两个账户：创建退款先占用，失败释放，成功再转入已成功金额。
 * 所有变更都发生在 Payment 聚合和同一 UoW 中，使并发退款无法静默超出支付金额。
 */
fun Payment.reserveRefund(amount: BigDecimal) {
    require(status == PaymentStatus.SUCCEEDED) { "支付单 $id 尚未成功，不能申请退款" }
    require(currentReviewEligibility().settlementEligible) { "支付单 $id 仍有未解决复核，暂不能退款" }
    require(amount > BigDecimal.ZERO)
    if (refundableAmount < amount) {
        throw RefundBudgetConflictException("支付单 $id 当前仅剩 $refundableAmount 可退款金额")
    }
    reservedRefundAmount = reservedRefundAmount.add(amount)
}

/** 明确失败或渠道拒绝时释放退款占用；未知结果继续占用，避免重复退款。 */
fun Payment.releaseRefundReservation(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO)
    require(reservedRefundAmount >= amount)
    reservedRefundAmount = reservedRefundAmount.subtract(amount)
}

/** 退款成功时把已占用预算原子转换为 successful，成功事实形成后不可回退。 */
fun Payment.convertRefundReservationToSuccess(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO)
    require(reservedRefundAmount >= amount)
    reservedRefundAmount = reservedRefundAmount.subtract(amount)
    successfulRefundAmount = successfulRefundAmount.add(amount)
}
