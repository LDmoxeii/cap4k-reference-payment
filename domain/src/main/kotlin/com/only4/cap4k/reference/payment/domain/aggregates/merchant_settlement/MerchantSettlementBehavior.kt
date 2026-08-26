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
    require(periodEnd > periodStart) { "结算周期结束时间必须晚于开始时间" }
    require(merchantId.isNotBlank()) { "商户身份不能为空" }
    require(channelId.isNotBlank()) { "渠道身份不能为空" }
    require(currency.isNotBlank()) { "币种不能为空" }
    require(scopeIdentity.isNotBlank()) { "结算范围身份不能为空" }
    requireTotalsMatchLines()
}

fun MerchantSettlement.onDeleted() {
}

/**
 * 授权确认会冻结结算范围、明细、费用和汇总。负净额继续停留在人工复核，零净额直接形成完成事实，
 * 只有正净额进入可执行状态；确认后的变化只能通过 adjustment/replacement 追加，不得原位改写。
 */
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
    )) { "结算单 $id 当前状态为 $status，不能确认组成" }
    require(!compositionFrozen) { "结算单 $id 的组成已经冻结" }
    require(settlementLines.isNotEmpty()) { "结算单 $id 没有结算明细" }
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

/**
 * 在同一 execution group 中创建资金划拨尝试。PROCESSING attempt 直接复用；明确失败后才允许人工重试；
 * RESULT_UNKNOWN 时禁止创建新 attempt，避免在外部结果不明时重复付款。
 */
fun MerchantSettlement.startExecutionAttempt(
    operatorIdentity: String,
    operatorRole: String,
    requestedAt: LocalDateTime,
    reviewAfterMinutes: Int,
    executionGroupIdentity: String,
    requestIdentity: String,
): SettlementExecutionAttempt {
    requireAuthorized(operatorIdentity, operatorRole)
    require(compositionFrozen) { "结算单 $id 的组成尚未确认" }
    require(netAmount.signum() > 0) { "结算单 $id 没有可划拨的正净额" }
    require(status != MerchantSettlementStatus.RESULT_UNKNOWN) {
        "结算单 $id 的执行结果仍未知，不能创建新的划拨尝试"
    }
    settlementExecutionAttempts.firstOrNull { it.status == SettlementExecutionAttemptStatus.PROCESSING }?.let { return it }
    require(status == MerchantSettlementStatus.CONFIRMED || status == MerchantSettlementStatus.FAILED) {
        "结算单 $id 当前状态为 $status，不能开始执行"
    }
    if (status == MerchantSettlementStatus.FAILED) {
        require(settlementExecutionAttempts.lastOrNull()?.finalResult in setOf(
            SettlementExecutionFinalResult.FAILED,
            SettlementExecutionFinalResult.GATEWAY_REJECTED,
        )) { "结算单 $id 只有在前一次尝试明确失败后才能重试" }
    }
    require(reviewAfterMinutes > 0) { "结果复核阈值必须大于零分钟" }
    require(executionGroupIdentity.isNotBlank()) { "执行组身份不能为空" }
    require(requestIdentity.isNotBlank()) { "请求身份不能为空" }
    if (this.executionGroupIdentity != null) {
        require(this.executionGroupIdentity == executionGroupIdentity) {
            "结算单 $id 的执行组身份不能改变"
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
        "结算执行尝试 $attemptId 当前状态为 ${attempt.status}，不能标记为已受理"
    }
    require(externalSettlementIdentity.isNotBlank()) { "外部结算身份不能为空" }
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
        .ifBlank { "资金划拨请求被渠道拒绝" }
    attempt.status = SettlementExecutionAttemptStatus.FAILED
    attempt.finalResult = SettlementExecutionFinalResult.GATEWAY_REJECTED
    attempt.rejectionSummary = summary
    lastRejectionSummary = summary
    status = MerchantSettlementStatus.FAILED
}

/**
 * 追加并裁决资金划拨结果：notification identity 负责重放幂等，payload fingerprint 负责识别同 identity 冲突，
 * 首次可信成功才形成一次 settled fact；终态后的相反结果只追加 receipt 并进入复核，绝不回退成功。
 */
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
        "不支持的结算结果：$result"
    }
    require(notificationIdentity.isNotBlank()) { "通知身份不能为空" }
    require(payloadFingerprint.isNotBlank()) { "payload 指纹不能为空" }
    val attempt = settlementExecutionAttempts.firstOrNull { it.id == attemptId }
        ?: run {
            val rejection = "结算执行尝试 $attemptId 不属于结算单 $id"
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
            return markResultConflict(attempt, existing, "通知 $notificationIdentity 被重复使用，但 payload 与首次接收内容冲突")
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
        !verified -> verificationSummary ?: "结算结果核验失败"
        attempt.channelId != channelId -> "渠道 $channelId 与结算执行尝试渠道 ${attempt.channelId} 不一致"
        attempt.executionGroupIdentity != executionGroupIdentity -> "执行组身份与结算执行尝试不一致"
        attempt.requestIdentity != requestIdentity -> "请求身份与结算执行尝试不一致"
        attempt.externalSettlementIdentity != null && attempt.externalSettlementIdentity != externalSettlementIdentity ->
            "外部结算身份与已受理的执行尝试不一致"
        attempt.amount.compareTo(amount) != 0 -> "结果金额 $amount 与结算金额 ${attempt.amount} 不一致"
        attempt.currency != currency.trim().uppercase() -> "结果币种与结算币种 ${attempt.currency} 不一致"
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
            return markResultConflict(attempt, receipt, "迟到的 $normalizedResult 结果与已终结的 ${priorFinal.name} 执行尝试冲突")
        }
        receipt.accepted = true
        receipt.decision = SettlementResultDisposition.ACCEPTED_DUPLICATE
        return resultOutcome(attempt, SettlementResultDisposition.ACCEPTED_DUPLICATE)
    }

    receipt.accepted = true
    attempt.verifiedNotificationCount += 1
    attempt.verdictSummary = verificationSummary ?: "核验通过"
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
            lastReviewSummary = "结算执行结果仍未知，需要在 ${attempt.reviewAfterAt} 后复核"
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
    lastReviewSummary = "未知结算结果已在 $reviewedAt 超过冻结的复核阈值"
    if (!settledFactFormed) status = MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED
    return true
}

/**
 * 以授权人工证据收敛 UNKNOWN attempt。裁决通过新的 append-only receipt 表达，
 * 与渠道 callback 共用 formSettledSuccess，保证两条成功路径只形成一个 completion event identity。
 */
fun MerchantSettlement.adjudicateUnknownResult(
    attemptId: SettlementExecutionAttemptId,
    operatorIdentity: String,
    operatorRole: String,
    finalResult: String,
    adjudicatedAt: LocalDateTime,
    evidence: String,
): SettlementResultRecordingOutcome {
    requireAuthorized(operatorIdentity, operatorRole)
    require(evidence.isNotBlank()) { "人工裁决证据不能为空" }
    val normalizedResult = finalResult.trim().uppercase()
    require(normalizedResult in setOf("SUCCESS", "FAILED")) {
        "人工裁决结果必须为 SUCCESS 或 FAILED"
    }
    val attempt = requireAttempt(attemptId)
    require(attempt.status in setOf(
        SettlementExecutionAttemptStatus.RESULT_UNKNOWN,
        SettlementExecutionAttemptStatus.REVIEW_REQUIRED,
        SettlementExecutionAttemptStatus.CONFLICT_REVIEW_REQUIRED,
    )) { "结算执行尝试 $attemptId 当前状态为 ${attempt.status}，不能人工裁决" }
    require(attempt.finalResult == SettlementExecutionFinalResult.UNKNOWN) {
        "结算执行尝试 $attemptId 的最终结果并非未知"
    }
    val externalIdentity = requireNotNull(attempt.externalSettlementIdentity) {
        "结算执行尝试 $attemptId 缺少外部结算身份"
    }
    val summary = "操作员 ${operatorIdentity.trim()} 完成人工裁决：${evidence.trim()}"
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
/** 只有尚未提交外部执行的结算单可以作废；释放 effective ownership 但完整保留历史组成和作废证据。 */
fun MerchantSettlement.voidBeforeExecution(
    operatorIdentity: String,
    operatorRole: String,
    reason: String,
    voidedAt: LocalDateTime,
) {
    requireAuthorized(operatorIdentity, operatorRole)
    require(reason.isNotBlank()) { "作废原因不能为空" }
    require(status in setOf(
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
    )) { "结算单 $id 当前状态为 $status，不能作废" }
    require(settlementExecutionAttempts.isEmpty()) { "结算单 $id 已开始外部执行，不能作废" }
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
    require(!compositionFrozen) { "结算单 $id 已确认，不能退回调整" }
    require(reason.isNotBlank()) { "退回调整原因不能为空" }
    voidBeforeExecution(
        operatorIdentity = operatorIdentity,
        operatorRole = operatorRole,
        reason = "RETURN_FOR_ADJUSTMENT: ${reason.trim()}",
        voidedAt = returnedAt,
    )
}

/**
 * 为 scope 和每条 source fact 形成稳定有效所有权，防止同一资金事实被两个有效结算单消费。
 * replacement 激活失败必须由同一 UoW 回滚 predecessor release 与新 ownership。
 */
fun MerchantSettlement.activateEffectiveOwnership() {
    require(status in setOf(
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
    )) { "结算单 $id 当前状态为 $status，不能激活有效所有权" }
    require(settlementLines.isNotEmpty()) { "结算单 $id 没有可激活的结算明细" }
    require(effectiveScopeIdentity == null || effectiveScopeIdentity == scopeIdentity) {
        "结算单 $id 的有效范围身份不能改变"
    }
    settlementLines.forEach { line ->
        val expected = stableIdentity("ACTIVE", line.sourceKind.name, line.sourceFactIdentity)
        require(line.effectiveConsumptionIdentity == null || line.effectiveConsumptionIdentity == expected) {
            "结算明细 ${line.id} 的有效消费身份不能改变"
        }
    }
    effectiveScopeIdentity = scopeIdentity
    settlementLines.forEach { line ->
        line.effectiveConsumptionIdentity = stableIdentity("ACTIVE", line.sourceKind.name, line.sourceFactIdentity)
    }
}

fun MerchantSettlement.requestActivation() {
    DomainEventSupervisor.instance.attach(
        MerchantSettlementActivationRequestedDomainEvent(id),
        this,
    )
}

fun MerchantSettlement.linkReplacement(replacementId: MerchantSettlementId) {
    require(status == MerchantSettlementStatus.VOIDED) { "只有已作废的结算单可以关联替代单" }
    replacementSettlementId = replacementId
}

fun MerchantSettlement.linkPredecessor(predecessorId: MerchantSettlementId) {
    require(status in setOf(
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
    )) { "只有未确认的结算单可以关联前置单" }
    require(predecessorSettlementId == null || predecessorSettlementId == predecessorId) {
        "结算单 $id 的前置单不能改变"
    }
    predecessorSettlementId = predecessorId
}

/**
 * 首次 accepted success 形成稳定的本地完成事实和 Domain Event；重复 callback 或人工重放返回 false，
 * 不创建第二个业务事件。事件 identity 与 SettlementId 稳定派生，并与业务状态在同一 UoW 提交。
 */
private fun MerchantSettlement.formSettledSuccess(completedAt: LocalDateTime): Boolean {
    status = MerchantSettlementStatus.SUCCEEDED
    this.completedAt = completedAt
    if (settledFactFormed) return false

    settledFactFormed = true
    DomainEventSupervisor.instance.attach(
        MerchantSettlementCompletedDomainEvent(
            eventIdentity = stableIdentity("MerchantSettlementCompleted:v1", id.toString()),
            merchantSettlementId = id,
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
    require(operatorIdentity.isNotBlank()) { "操作员身份不能为空" }
    require(operatorRole.trim().uppercase() == SETTLEMENT_OPERATOR_ROLE) {
        "当前操作员角色无权处理商户结算"
    }
}

private fun MerchantSettlement.requireAttempt(attemptId: SettlementExecutionAttemptId): SettlementExecutionAttempt =
    settlementExecutionAttempts.firstOrNull { it.id == attemptId }
        ?: error("结算执行尝试 $attemptId 不属于结算单 $id")

/**
 * 以 SettlementLine 为财务组成真源核对 root 汇总：Payment 为收入减冻结手续费，Refund 为负向金额，
 * Adjustment 按 signedNetAmount 计入。任何币种、identity 或汇总不一致都必须整体回滚。
 */
private fun MerchantSettlement.requireTotalsMatchLines() {
    require(settlementLines.all { it.currency == currency }) { "所有结算明细必须使用结算币种 $currency" }
    require(settlementLines.map { it.lineIdentity }.distinct().size == settlementLines.size) {
        "结算明细身份必须唯一"
    }
    require(settlementLines.map { it.sourceKind to it.sourceFactIdentity }.distinct().size == settlementLines.size) {
        "结算来源事实必须唯一"
    }
    val calculatedPaymentGross = settlementLines.filter { it.transactionKind.name == "PAYMENT" }
        .fold(BigDecimal.ZERO) { total, line -> total + line.grossAmount }
    val calculatedRefundGross = settlementLines.filter { it.transactionKind.name == "REFUND" }
        .fold(BigDecimal.ZERO) { total, line -> total + line.grossAmount }
    val calculatedFees = settlementLines.fold(BigDecimal.ZERO) { total, line -> total + line.feeAmount }
    val calculatedAdjustments = settlementLines.filter { it.sourceKind.name == "ADJUSTMENT" }
        .fold(BigDecimal.ZERO) { total, line -> total + line.signedNetAmount }
    val calculatedNet = settlementLines.fold(BigDecimal.ZERO) { total, line -> total + line.signedNetAmount }
    require(paymentGrossAmount.compareTo(calculatedPaymentGross) == 0) { "支付总额与结算明细汇总不一致" }
    require(refundGrossAmount.compareTo(calculatedRefundGross) == 0) { "退款总额与结算明细汇总不一致" }
    require(feeTotalAmount.compareTo(calculatedFees) == 0) { "手续费总额与结算明细汇总不一致" }
    require(adjustmentTotalAmount.compareTo(calculatedAdjustments) == 0) { "调整总额与结算明细汇总不一致" }
    require(netAmount.compareTo(calculatedNet) == 0) { "净结算金额与结算明细汇总不一致" }
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
