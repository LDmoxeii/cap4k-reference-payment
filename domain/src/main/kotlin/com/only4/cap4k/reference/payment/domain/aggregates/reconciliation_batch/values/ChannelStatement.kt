package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import java.time.Instant
import java.time.LocalDate

@DesignBlockMetadata(
    tag = "value_object",
    name = "ChannelStatement",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values",
    description = "Immutable revisioned channel statement returned by PullChannelStatement",
    aggregates = ["ReconciliationBatch"],
    family = "value-object"
)
data class ChannelStatement(
    val channelId: String,
    val currency: String,
    val reconciliationDate: LocalDate,
    val businessTimezone: String,
    val statementIdentity: String,
    val statementRevision: String,
    val completeness: StatementCompleteness,
    val fetchedAt: Instant,
    val records: List<ChannelStatementRecord>
)
