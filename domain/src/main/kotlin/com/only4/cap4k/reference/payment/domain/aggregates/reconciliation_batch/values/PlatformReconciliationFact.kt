package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "value_object",
    name = "PlatformReconciliationFact",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values",
    description = "Immutable payment or refund fact projected for reconciliation without an ORM aggregate relation",
    aggregates = ["ReconciliationBatch"],
    family = "value-object"
)
data class PlatformReconciliationFact(
    val factIdentity: String,
    val transactionKind: ReconciliationTransactionKind,
    val paymentId: String?,
    val paymentAttemptId: String?,
    val refundId: String?,
    val refundAttemptId: String?,
    val channelTransactionIdentity: String,
    val amount: BigDecimal,
    val currency: String,
    val rawStatus: String,
    val occurredAt: Instant,
    val recordedAt: Instant
)
