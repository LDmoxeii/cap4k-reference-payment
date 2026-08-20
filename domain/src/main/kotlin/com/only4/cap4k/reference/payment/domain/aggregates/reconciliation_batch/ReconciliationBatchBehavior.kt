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

/** Records that the statement provider did not produce a usable statement for this scope. */
fun ReconciliationBatch.markStatementFetchFailed(at: LocalDateTime, reason: String) {
    status = if (at.isAfter(statementWaitDeadlineAt)) {
        ReconciliationBatchStatus.REVIEW_REQUIRED
    } else {
        ReconciliationBatchStatus.FETCH_FAILED
    }
    settlementBlocked = true
    blockingReason = reason.takeIf { it.isNotBlank() } ?: "Channel statement provider failed"
    completedAt = null
}

/** Reconciles one immutable statement revision and retains all earlier revisions as history. */
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
    require(statement.channelId == channelId) { "Statement channel does not belong to this batch" }
    require(statement.currency == currency) { "Statement currency does not belong to this batch" }
    require(statement.reconciliationDate == reconciliationDate) { "Statement date does not belong to this batch" }

    reconciliationRuns.firstOrNull {
        it.statementIdentity == statement.statementIdentity && it.statementRevision == statement.statementRevision
    }?.let { return ReconciliationRunResult(it, true) }

    reconciliationRuns.filter { it.status != ReconciliationRunStatus.FAILED }
        .forEach { it.status = ReconciliationRunStatus.SUPERSEDED }

    val items = classify(statement.records, platformFacts)
    val matched = items.count { it.differenceType == ReconciliationDifferenceType.MATCHED }
    val differences = items.size - matched
    val unresolved = items.count { !it.resolved }
    val completedAt = startedAt
    val run = ReconciliationRun(
        statementIdentity = statement.statementIdentity,
        statementRevision = statement.statementRevision,
        statementCompleteness = statement.completeness,
        status = ReconciliationRunStatus.COMPLETED,
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
    currentEffectiveRunId = runId.toString()
    applyEffectiveRun(run, completedAt)
    return ReconciliationRunResult(run, false)
}

/** Appends every authorization attempt. Denied attempts remain audit evidence and never resolve an item. */
fun ReconciliationBatch.appendDisposition(
    differenceIdentity: String,
    creation: ReconciliationDispositionCreation,
    confirmation: ReconciliationConfirmationFactCreation? = null
): ReconciliationDisposition {
    val run = effectiveRun()
    val item = run.reconciliationItems.firstOrNull { it.differenceIdentity == differenceIdentity }
        ?: throw IllegalArgumentException("Unknown reconciliation difference: $differenceIdentity")
    val authorized = creation.authorizationResult == DispositionAuthorization.AUTHORIZED
    val conclusion = if (authorized) {
        require(creation.status == ReconciliationDispositionStatus.APPLIED) {
            "Authorized disposition must be APPLIED"
        }
        requireNotNull(creation.conclusion) {
            "Authorized disposition must declare a conclusion"
        }.also { authorizedConclusion ->
            if (authorizedConclusion == ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT) {
                require(creation.settlementImpact == SettlementImpact.CONFIRMS_SETTLEMENT_FACT) {
                    "Platform confirmation must declare CONFIRMS_SETTLEMENT_FACT"
                }
                val requiredConfirmation = requireNotNull(confirmation) {
                    "Platform confirmation conclusion requires a confirmation fact"
                }
                item.requireConfirmationEligibility(requiredConfirmation)
            } else {
                require(confirmation == null) {
                    "A confirmation fact is only valid for CONFIRM_PLATFORM_FACT"
                }
            }
        }
    } else {
        require(creation.status == ReconciliationDispositionStatus.REJECTED) {
            "Denied disposition must be REJECTED"
        }
        require(confirmation == null) { "Denied disposition cannot create a confirmation fact" }
        null
    }

    val disposition = ReconciliationDisposition(
        creation.operatorIdentity, creation.operatorRole, creation.authorizationResult,
        creation.status, creation.conclusion, creation.settlementImpact,
        creation.evidence, creation.followUp, creation.disposedAt
    )
    item.reconciliationDispositions.add(disposition)
    if (authorized) {
        if (conclusion == ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT) {
            item.appendConfirmation(requireNotNull(confirmation))
        }
        item.resolved = conclusion != ReconciliationDispositionConclusion.ESCALATE
        item.settlementBlocked = !item.resolved || creation.settlementImpact == SettlementImpact.BLOCKS_SETTLEMENT
    }
    recalculate(run, creation.disposedAt)
    return disposition
}

private fun ReconciliationItem.requireConfirmationEligibility(creation: ReconciliationConfirmationFactCreation) {
    require(
        differenceType == ReconciliationDifferenceType.CHANNEL_ONLY ||
            differenceType == ReconciliationDifferenceType.STATUS_MISMATCH
    ) { "Only CHANNEL_ONLY or STATUS_MISMATCH can form a platform confirmation fact" }
    require(channelRawStatus?.uppercase() in setOf("SUCCESS", "SUCCEEDED")) {
        "Confirmation requires channel success evidence"
    }
    val evidenceAmount = requireNotNull(channelAmount) { "Confirmation requires channel amount evidence" }
    require(evidenceAmount.compareTo(creation.amount) == 0) {
        "Confirmation amount does not match channel evidence"
    }
    require(channelCurrency == creation.currency) { "Confirmation currency does not match channel evidence" }
    require(channelTransactionIdentity == creation.externalTransactionIdentity) {
        "Confirmation transaction identity does not match channel evidence"
    }
    require(transactionKind == creation.transactionKind) { "Confirmation transaction kind does not match item" }
}

fun ReconciliationItem.appendConfirmation(creation: ReconciliationConfirmationFactCreation): ReconciliationConfirmationFact {
    require(creation.sourceDifferenceIdentity == differenceIdentity) { "Confirmation source does not match item" }
    val fact = ReconciliationConfirmationFact(
        creation.sourceDifferenceIdentity, creation.operatorIdentity, creation.confirmationReason,
        creation.evidence, creation.transactionKind, creation.amount, creation.currency,
        creation.externalTransactionIdentity, creation.paymentId, creation.refundId, creation.confirmedAt
    )
    reconciliationConfirmationFacts.add(fact)
    return fact
}

fun ReconciliationBatch.recalculateCompletion(at: LocalDateTime = LocalDateTime.now()) = recalculate(effectiveRun(), at)

private fun ReconciliationBatch.effectiveRun(): ReconciliationRun =
    reconciliationRuns.lastOrNull { it.status != ReconciliationRunStatus.SUPERSEDED }
        ?: throw IllegalStateException("Batch has no effective reconciliation run")

private fun ReconciliationBatch.recalculate(run: ReconciliationRun, at: LocalDateTime) {
    run.unresolvedDifferenceCount = run.reconciliationItems.count { !it.resolved }
    applyEffectiveRun(run, at)
}

private fun ReconciliationBatch.applyEffectiveRun(run: ReconciliationRun, at: LocalDateTime) {
    matchedCount = run.matchedCount
    differenceCount = run.differenceCount
    unresolvedDifferenceCount = run.unresolvedDifferenceCount
    val complete = run.statementCompleteness == StatementCompleteness.COMPLETE
    settlementBlocked = !complete || run.reconciliationItems.any { it.settlementBlocked }
    when {
        !complete -> {
            status = ReconciliationBatchStatus.REVIEW_REQUIRED
            blockingReason = "Statement is not complete"
            completedAt = null
        }
        unresolvedDifferenceCount > 0 || settlementBlocked -> {
            status = ReconciliationBatchStatus.AWAITING_DISPOSITION
            blockingReason = "Unresolved or settlement-blocking reconciliation differences"
            completedAt = null
        }
        else -> {
            status = ReconciliationBatchStatus.COMPLETED
            blockingReason = null
            completedAt = at
        }
    }
}

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
    val matched = type == ReconciliationDifferenceType.MATCHED
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
        matchingBasis = basis, auxiliaryMatchApproved = false, resolved = matched, settlementBlocked = !matched
    )
}
