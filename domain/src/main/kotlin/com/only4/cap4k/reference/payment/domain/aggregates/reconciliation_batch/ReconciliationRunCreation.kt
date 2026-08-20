package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch

import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationRunStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import java.time.LocalDateTime

data class ReconciliationRunCreation(
    val statementIdentity: String,
    val statementRevision: String,
    val statementCompleteness: StatementCompleteness,
    val status: ReconciliationRunStatus,
    val fetchedAt: LocalDateTime,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val channelRecordCount: Int = 0,
    val platformFactCount: Int = 0,
    val matchedCount: Int = 0,
    val differenceCount: Int = 0,
    val unresolvedDifferenceCount: Int = 0,
    val failureSummary: String?,
    val reconciliationItems: List<ReconciliationItemCreation> = emptyList()
)
