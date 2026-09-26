package com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill

import com.only4.cap4k.ddd.core.Mediator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The bill aggregate is the durable authority for the provider input.  A reconciliation run may
 * reference a revision, but it never owns, rewrites, or manufactures that revision's evidence.
 */
fun AuthoritativeBill.onCreate() {}
fun AuthoritativeBill.onDeleted() {}

data class BillRevisionAppendResult(
    val revision: BillRevision,
    val idempotentReplay: Boolean,
    val becameCurrent: Boolean,
)

/**
 * Appends a revision exactly once.  The provider identity and the revision are immutable; a
 * repeated revision with a different fingerprint is a provider conflict rather than a mutable
 * update.  Lower/late revisions remain queryable but can never move the current pointer back.
 */
fun AuthoritativeBill.appendRevision(creation: BillRevisionCreation): BillRevisionAppendResult {
    require(creation.revision.matches(POSITIVE_REVISION)) { "账单 revision 必须为正整数" }
    require(creation.rawEvidence.isNotBlank()) { "账单原始证据不能为空" }
    require(creation.payloadFingerprint.isNotBlank()) { "账单正文指纹不能为空" }
    require(creation.records.map { it.recordIdentity }.distinct().size == creation.records.size) {
        "同一账单修订中的 recordIdentity 必须唯一"
    }
    creation.records.forEach { record ->
        require(record.recordIdentity.isNotBlank()) { "账单行身份不能为空" }
        require(record.channelTransactionIdentity.isNotBlank()) { "渠道交易身份不能为空" }
        require(record.amount.signum() >= 0) { "账单行金额不能为负" }
        require(record.currency.uppercase() == currency.uppercase()) { "账单行币种必须与账单币种一致" }
        require(record.rawStatus.isNotBlank()) { "账单行状态不能为空" }
        require(record.rawEvidence.isNotBlank()) { "账单行原始证据不能为空" }
    }

    billRevisions.firstOrNull { it.revision == creation.revision }?.let { existing ->
        require(
            existing.payloadFingerprint == creation.payloadFingerprint &&
                existing.completeness == creation.completeness &&
                existing.rawEvidence == creation.rawEvidence &&
                existing.publishedAt == LocalDateTime.ofInstant(creation.publishedAt, ZoneOffset.UTC) &&
                existing.records.size == creation.records.size &&
                creation.records.all { incoming ->
                    val record = existing.records.firstOrNull { it.recordIdentity == incoming.recordIdentity }
                        ?: return@all false
                    record.recordIdentity == incoming.recordIdentity &&
                        record.channelTransactionIdentity == incoming.channelTransactionIdentity &&
                        record.transactionKind == incoming.transactionKind &&
                        record.amount.compareTo(incoming.amount) == 0 &&
                        record.currency.equals(incoming.currency, ignoreCase = true) &&
                        record.rawStatus == incoming.rawStatus &&
                        record.occurredAt == incoming.occurredAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) } &&
                        record.receivedAt == LocalDateTime.ofInstant(incoming.receivedAt, ZoneOffset.UTC) &&
                        record.rawEvidence == incoming.rawEvidence
                }
        ) {
            "相同账单 revision 的正文指纹冲突或证据冲突"
        }
        return BillRevisionAppendResult(existing, idempotentReplay = true, becameCurrent = false)
    }

    val revision = BillRevision(
        revision = creation.revision,
        completeness = creation.completeness,
        rawEvidence = creation.rawEvidence,
        payloadFingerprint = creation.payloadFingerprint,
        publishedAt = LocalDateTime.ofInstant(creation.publishedAt, ZoneOffset.UTC),
    )
    revision.id = BillRevisionId.of(Mediator.identifiers.next("uuid7", String::class))
    creation.records.forEach { recordCreation ->
        val record = BillRevisionRecord(
            recordIdentity = recordCreation.recordIdentity,
            channelTransactionIdentity = recordCreation.channelTransactionIdentity,
            transactionKind = recordCreation.transactionKind,
            amount = recordCreation.amount,
            currency = recordCreation.currency.uppercase(),
            rawStatus = recordCreation.rawStatus,
            occurredAt = recordCreation.occurredAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
            receivedAt = LocalDateTime.ofInstant(recordCreation.receivedAt, ZoneOffset.UTC),
            rawEvidence = recordCreation.rawEvidence,
        )
        record.id = BillRevisionRecordId.of(Mediator.identifiers.next("uuid7", String::class))
        revision.records.add(record)
    }
    billRevisions.add(revision)

    val becameCurrent = currentRevision == null || compareRevision(creation.revision, currentRevision!!) > 0
    if (becameCurrent) {
        currentRevision = creation.revision
        currentRevisionId = revision.id.toString()
    }
    return BillRevisionAppendResult(revision, idempotentReplay = false, becameCurrent = becameCurrent)
}

/**
 * Signals are durable receipt facts.  A retry keeps the same signal identity and only increases
 * its recorded provider-read attempt count; signal data cannot replace the bill body.
 */
fun AuthoritativeBill.recordBillAvailableSignal(creation: BillAvailableSignalCreation): BillAvailableSignal {
    require(creation.signalIdentity.isNotBlank()) { "账单可用信号身份不能为空" }
    require(creation.announcedRevision.matches(POSITIVE_REVISION)) { "账单可用信号 revision 必须为正整数" }
    val existing = billAvailableSignals.firstOrNull { it.signalIdentity == creation.signalIdentity }
    if (existing != null) {
        require(existing.announcedRevision == creation.announcedRevision) { "相同账单可用信号身份声明了不同 revision" }
        return existing
    }
    return BillAvailableSignal(
        signalIdentity = creation.signalIdentity,
        announcedRevision = creation.announcedRevision,
        publishedAt = LocalDateTime.ofInstant(creation.publishedAt, ZoneOffset.UTC),
        receivedAt = LocalDateTime.ofInstant(creation.receivedAt, ZoneOffset.UTC),
    ).also { signal ->
        signal.id = BillAvailableSignalId.of(Mediator.identifiers.next("uuid7", String::class))
        billAvailableSignals.add(signal)
    }
}

fun AuthoritativeBill.recordReadAttempt(signalIdentity: String, at: Instant, diagnostic: String?): BillAvailableSignal {
    val signal = billAvailableSignals.firstOrNull { it.signalIdentity == signalIdentity }
        ?: throw IllegalArgumentException("未找到账单可用信号：$signalIdentity")
    readAttemptCount += 1
    lastReadAttemptAt = LocalDateTime.ofInstant(at, ZoneOffset.UTC)
    lastFetchDiagnostic = diagnostic?.trim()?.takeIf { it.isNotBlank() }
    signal.fetchAttemptCount += 1
    signal.diagnostic = lastFetchDiagnostic
    return signal
}

fun AuthoritativeBill.currentBillRevision(): BillRevision? = currentRevisionId?.let { currentId ->
    billRevisions.firstOrNull { it.id.toString() == currentId }
} ?: currentRevision?.let { revision -> billRevisions.firstOrNull { it.revision == revision } }

private val POSITIVE_REVISION = Regex("[1-9][0-9]*")

private fun compareRevision(left: String, right: String): Int = left.toBigInteger().compareTo(right.toBigInteger())
