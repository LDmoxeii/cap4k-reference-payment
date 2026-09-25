package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.*
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatementRecord
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.PlatformReconciliationFact
import java.time.LocalDateTime
import java.time.ZoneOffset

fun ReconciliationBatch.onCreate() {}
fun ReconciliationBatch.onDeleted() {}

data class ReconciliationRunResult(val run: ReconciliationRun, val idempotentReplay: Boolean)

/**
 * 记录账单 provider 未能提供可用账单的事实。失败原因只保存受控中文摘要；
 * 原始异常留在应用日志，不能把“不可用”伪装成空账单并完成对账。
 */
fun ReconciliationBatch.markStatementFetchFailed(at: LocalDateTime, reason: String) {
    status = if (at.isAfter(statementWaitDeadlineAt)) {
        ReconciliationBatchStatus.REVIEW_REQUIRED
    } else {
        ReconciliationBatchStatus.FETCH_FAILED
    }
    settlementBlocked = true
    blockingReason = reason.takeIf { it.isNotBlank() } ?: "渠道账单暂时不可用"
    completedAt = null
}

/**
 * 追加一个不可变账单 revision。相同 identity+revision 重放直接复用；更高 revision 成为 effective run，
 * 迟到的更低 revision 只保留历史且不能回退 currentEffectiveRunId。旧 run/items 永不覆盖。
 */
fun ReconciliationBatch.appendReconciliationRun(
    statement: ChannelStatement,
    platformFacts: List<PlatformReconciliationFact>,
    startedAt: LocalDateTime = LocalDateTime.now()
): ReconciliationRunResult = appendReconciliationRun(
    statement = statement,
    platformFacts = platformFacts,
    startedAt = startedAt,
    runId = ReconciliationRunId.of(Mediator.identifiers.next("uuid7", String::class)),
)

internal fun ReconciliationBatch.appendReconciliationRun(
    statement: ChannelStatement,
    platformFacts: List<PlatformReconciliationFact>,
    startedAt: LocalDateTime,
    runId: ReconciliationRunId,
): ReconciliationRunResult {
    require(statement.channelId == channelId) { "账单渠道不属于当前对账批次" }
    require(statement.currency == currency) { "账单币种不属于当前对账批次" }
    require(statement.reconciliationDate == reconciliationDate) { "账单业务日不属于当前对账批次" }

    reconciliationRuns.firstOrNull {
        it.statementIdentity == statement.statementIdentity && it.statementRevision == statement.statementRevision
    }?.let { return ReconciliationRunResult(it, true) }

    val effectiveRun = reconciliationRuns.lastOrNull {
        it.status != ReconciliationRunStatus.SUPERSEDED && it.status != ReconciliationRunStatus.FAILED
    }
    val isLateOlderRevision = effectiveRun != null &&
        effectiveRun.statementIdentity == statement.statementIdentity &&
        compareStatementRevision(statement.statementRevision, effectiveRun.statementRevision) < 0
    if (!isLateOlderRevision) {
        reconciliationRuns.filter { it.status != ReconciliationRunStatus.FAILED }
            .forEach { it.status = ReconciliationRunStatus.SUPERSEDED }
    }

    val items = classify(statement.records, platformFacts)
    val matched = items.count { it.differenceType == ReconciliationDifferenceType.MATCHED }
    val differences = items.size - matched
    val unresolved = items.count { !it.resolved }
    val readyToComplete = statement.completeness == StatementCompleteness.COMPLETE &&
        unresolved == 0 && items.none { it.settlementBlocked }
    val completedAt = startedAt.takeIf { readyToComplete || isLateOlderRevision }
    val run = ReconciliationRun(
        statementIdentity = statement.statementIdentity,
        statementRevision = statement.statementRevision,
        statementCompleteness = statement.completeness,
        status = when {
            isLateOlderRevision -> ReconciliationRunStatus.SUPERSEDED
            readyToComplete -> ReconciliationRunStatus.COMPLETED
            else -> ReconciliationRunStatus.RECONCILING
        },
        fetchedAt = LocalDateTime.ofInstant(statement.fetchedAt, ZoneOffset.UTC),
        startedAt = startedAt,
        completedAt = completedAt,
        channelRecordCount = statement.records.size,
        platformFactCount = platformFacts.size,
        matchedCount = matched,
        differenceCount = differences,
        unresolvedDifferenceCount = unresolved,
        failureSummary = null
    )
    run.id = runId
    items.forEach(run.reconciliationItems::add)
    reconciliationRuns.add(run)
    if (!isLateOlderRevision) {
        currentEffectiveRunId = runId.toString()
        applyEffectiveRun(run, startedAt)
    }
    return ReconciliationRunResult(run, false)
}

/**
 * 追加每一次差异处置尝试。未授权动作也要保留 REJECTED 审计记录，但不能解决差异；
 * 只有授权结论才能改变 resolved/settlementBlocked，且仅 CONFIRM_PLATFORM_FACT 可以形成确认事实。
 */
fun ReconciliationBatch.appendDisposition(
    differenceIdentity: String,
    creation: ReconciliationDispositionCreation,
    confirmation: ReconciliationConfirmationFactCreation? = null
): ReconciliationDisposition {
    val item = requireDispositionEligible(differenceIdentity, creation, confirmation)
    val authorized = creation.authorizationResult == DispositionAuthorization.AUTHORIZED
    val conclusion = creation.conclusion

    val disposition = ReconciliationDisposition(
        creation.operatorIdentity, creation.operatorRole, creation.authorizationResult,
        creation.status, creation.conclusion, creation.settlementImpact,
        creation.reason, creation.evidence, creation.followUp, creation.disposedAt
    )
    item.reconciliationDispositions.add(disposition)
    if (authorized) {
        if (conclusion == ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT) {
            item.appendConfirmation(requireNotNull(confirmation))
        }
        item.resolved = conclusion != ReconciliationDispositionConclusion.ESCALATE
        item.settlementBlocked = !item.resolved || creation.settlementImpact == SettlementImpact.BLOCKS_SETTLEMENT
    }
    recalculate(effectiveRun(), creation.disposedAt)
    return disposition
}

/** Pure domain preflight shared by the application command and the append boundary. */
fun ReconciliationBatch.requireDispositionEligible(
    differenceIdentity: String,
    creation: ReconciliationDispositionCreation,
    confirmation: ReconciliationConfirmationFactCreation? = null,
): ReconciliationItem {
    val run = effectiveRun()
    val item = run.reconciliationItems.firstOrNull { it.differenceIdentity == differenceIdentity }
        ?: throw IllegalArgumentException("未找到对账差异：$differenceIdentity")
    val authorized = creation.authorizationResult == DispositionAuthorization.AUTHORIZED
    if (authorized) {
        require(creation.status == ReconciliationDispositionStatus.APPLIED) {
            "已授权的差异处置必须使用 APPLIED 状态"
        }
        requireNotNull(creation.conclusion) {
            "已授权的差异处置必须声明结论"
        }.also { authorizedConclusion ->
            if (authorizedConclusion == ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT) {
                require(creation.settlementImpact == SettlementImpact.CONFIRMS_SETTLEMENT_FACT) {
                    "确认平台事实时必须声明 CONFIRMS_SETTLEMENT_FACT 结算影响"
                }
                val requiredConfirmation = requireNotNull(confirmation) {
                    "确认平台事实的结论必须同时提供确认事实"
                }
                item.requireConfirmationEligibility(requiredConfirmation, channelId)
            } else {
                require(confirmation == null) {
                    "只有 CONFIRM_PLATFORM_FACT 结论可以携带确认事实"
                }
            }
        }
    } else {
        require(creation.status == ReconciliationDispositionStatus.REJECTED) {
            "未授权的差异处置必须使用 REJECTED 状态"
        }
        require(confirmation == null) { "未授权的差异处置不能创建确认事实" }
    }
    return item
}

private fun ReconciliationItem.requireConfirmationEligibility(
    creation: ReconciliationConfirmationFactCreation,
    expectedChannelId: String,
) {
    require(creation.merchantId.isNotBlank()) { "确认事实的商户身份不能为空" }
    require(creation.channelId == expectedChannelId) { "确认事实的渠道不属于当前对账批次" }
    require(
        differenceType == ReconciliationDifferenceType.CHANNEL_ONLY ||
            differenceType == ReconciliationDifferenceType.STATUS_MISMATCH
    ) { "只有 CHANNEL_ONLY 或 STATUS_MISMATCH 差异可以形成平台确认事实" }
    require(channelRawStatus?.uppercase() in setOf("SUCCESS", "SUCCEEDED")) {
        "确认事实必须基于渠道成功证据"
    }
    val evidenceAmount = requireNotNull(channelAmount) { "确认事实缺少渠道金额证据" }
    require(evidenceAmount.compareTo(creation.amount) == 0) {
        "确认金额与渠道证据不一致"
    }
    require(channelCurrency == creation.currency) { "确认币种与渠道证据不一致" }
    require(channelTransactionIdentity == creation.externalTransactionIdentity) {
        "确认交易身份与渠道证据不一致"
    }
    require(transactionKind == creation.transactionKind) { "确认交易类型与差异项不一致" }
}

fun ReconciliationItem.appendConfirmation(creation: ReconciliationConfirmationFactCreation): ReconciliationConfirmationFact {
    require(creation.sourceDifferenceIdentity == differenceIdentity) { "确认事实来源与差异项不一致" }
    val fact = ReconciliationConfirmationFact(
        sourceDifferenceIdentity = creation.sourceDifferenceIdentity,
        merchantId = creation.merchantId,
        channelId = creation.channelId,
        operatorIdentity = creation.operatorIdentity,
        confirmationReason = creation.confirmationReason,
        evidence = creation.evidence,
        transactionKind = creation.transactionKind,
        amount = creation.amount,
        currency = creation.currency,
        externalTransactionIdentity = creation.externalTransactionIdentity,
        paymentId = creation.paymentId,
        refundId = creation.refundId,
        confirmedAt = creation.confirmedAt,
    )
    reconciliationConfirmationFacts.add(fact)
    return fact
}

fun ReconciliationBatch.recalculateCompletion(at: LocalDateTime = LocalDateTime.now()) = recalculate(effectiveRun(), at)

private fun ReconciliationBatch.effectiveRun(): ReconciliationRun =
    reconciliationRuns.lastOrNull { it.status != ReconciliationRunStatus.SUPERSEDED }
        ?: throw IllegalStateException("对账批次没有当前有效运行")

private fun compareStatementRevision(left: String, right: String): Int {
    val leftNumeric = left.toBigIntegerOrNull()
    val rightNumeric = right.toBigIntegerOrNull()
    return if (leftNumeric != null && rightNumeric != null) {
        leftNumeric.compareTo(rightNumeric)
    } else {
        left.compareTo(right)
    }
}

private fun ReconciliationBatch.recalculate(run: ReconciliationRun, at: LocalDateTime) {
    run.unresolvedDifferenceCount = run.reconciliationItems.count { !it.resolved }
    val readyToComplete = run.statementCompleteness == StatementCompleteness.COMPLETE &&
        run.unresolvedDifferenceCount == 0 && run.reconciliationItems.none { it.settlementBlocked }
    run.status = if (readyToComplete) ReconciliationRunStatus.COMPLETED else ReconciliationRunStatus.RECONCILING
    run.completedAt = at.takeIf { readyToComplete }
    applyEffectiveRun(run, at)
}

/**
 * 仅把 current effective run 投影到批次摘要。账单完整性和每个 item 的 settlementBlocked 共同决定完成状态；
 * 旧 run 保持不可变，人工处置后只重算当前 run 的派生计数和阻断原因。
 */
private fun ReconciliationBatch.applyEffectiveRun(run: ReconciliationRun, at: LocalDateTime) {
    matchedCount = run.matchedCount
    differenceCount = run.differenceCount
    unresolvedDifferenceCount = run.unresolvedDifferenceCount
    val complete = run.statementCompleteness == StatementCompleteness.COMPLETE
    settlementBlocked = !complete || run.reconciliationItems.any { it.settlementBlocked }
    when {
        !complete -> {
            status = ReconciliationBatchStatus.REVIEW_REQUIRED
            blockingReason = "渠道账单不完整"
            completedAt = null
        }
        unresolvedDifferenceCount > 0 || settlementBlocked -> {
            status = ReconciliationBatchStatus.AWAITING_DISPOSITION
            blockingReason = "仍有未解决或阻断结算的对账差异"
            completedAt = null
        }
        else -> {
            status = ReconciliationBatchStatus.COMPLETED
            blockingReason = null
            completedAt = at
        }
    }
}

/**
 * 逐笔匹配优先使用稳定渠道交易身份，再依次比较交易类型、币种、金额和状态。
 * 相同渠道身份的第二条及后续记录标记为 DUPLICATE；金额相同本身绝不能成为自动关联依据。
 */
private fun classify(
    records: List<ChannelStatementRecord>, facts: List<PlatformReconciliationFact>
): List<ReconciliationItem> {
    val remainingFacts = facts.toMutableList()
    val duplicateIds = records.groupingBy { it.channelTransactionIdentity }.eachCount().filterValues { it > 1 }.keys
    val result = mutableListOf<ReconciliationItem>()
    records.forEachIndexed { index, record ->
        if (record.channelTransactionIdentity in duplicateIds &&
            records.indexOfFirst { it.channelTransactionIdentity == record.channelTransactionIdentity } != index
        ) {
            result += item(record, null, ReconciliationDifferenceType.DUPLICATE_CHANNEL_RECORD, "duplicate-channel-identity")
            return@forEachIndexed
        }
        val fact = remainingFacts.firstOrNull { it.channelTransactionIdentity == record.channelTransactionIdentity }
        if (fact == null) result += item(record, null, ReconciliationDifferenceType.CHANNEL_ONLY, "channel-identity")
        else {
            remainingFacts.remove(fact)
            val type = when {
                fact.transactionKind != record.transactionKind -> ReconciliationDifferenceType.UNMATCHED
                fact.currency != record.currency -> ReconciliationDifferenceType.CURRENCY_MISMATCH
                fact.amount.compareTo(record.amount) != 0 -> ReconciliationDifferenceType.AMOUNT_MISMATCH
                fact.rawStatus != record.rawStatus -> ReconciliationDifferenceType.STATUS_MISMATCH
                else -> ReconciliationDifferenceType.MATCHED
            }
            result += item(record, fact, type, "channel-transaction-identity")
        }
    }
    remainingFacts.forEach { result += item(null, it, ReconciliationDifferenceType.PLATFORM_ONLY, "platform-identity") }
    return result
}

private fun item(record: ChannelStatementRecord?, fact: PlatformReconciliationFact?, type: ReconciliationDifferenceType, basis: String): ReconciliationItem {
    val identity = record?.recordIdentity ?: fact!!.factIdentity
    val matched = type == ReconciliationDifferenceType.MATCHED && fact?.settlementEligible != false
    return ReconciliationItem(
        differenceIdentity = "$identity:${type.name}", transactionKind = record?.transactionKind ?: fact!!.transactionKind,
        differenceType = type, channelRecordIdentity = record?.recordIdentity,
        channelTransactionIdentity = record?.channelTransactionIdentity, channelAmount = record?.amount,
        channelCurrency = record?.currency, channelRawStatus = record?.rawStatus,
        channelOccurredAt = record?.occurredAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
        channelReceivedAt = record?.receivedAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
        platformFactIdentity = fact?.factIdentity, paymentId = fact?.paymentId, paymentAttemptId = fact?.paymentAttemptId,
        refundId = fact?.refundId, refundAttemptId = fact?.refundAttemptId,
        platformTransactionIdentity = fact?.channelTransactionIdentity, platformAmount = fact?.amount,
        platformCurrency = fact?.currency, platformRawStatus = fact?.rawStatus,
        platformOccurredAt = fact?.occurredAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
        platformRecordedAt = fact?.recordedAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
        paymentReviewIdentitySnapshot = fact?.paymentReviewIdentitySnapshot,
        paymentReviewSummary = fact?.paymentReviewSummary,
        matchingBasis = basis, auxiliaryMatchApproved = false, resolved = matched, settlementBlocked = !matched
    )
}
