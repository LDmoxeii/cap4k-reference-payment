package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "value_object",
    name = "ChannelStatementRecord",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values",
    description = "Immutable channel statement record used for deterministic reconciliation matching",
    aggregates = ["ReconciliationBatch"],
    family = "value-object"
)
data class ChannelStatementRecord(
    val recordIdentity: String,
    val transactionKind: ReconciliationTransactionKind,
    val channelTransactionIdentity: String,
    val amount: BigDecimal,
    val currency: String,
    val rawStatus: String,
    val occurredAt: Instant,
    val receivedAt: Instant
)
