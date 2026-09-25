package com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill

import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import java.math.BigDecimal
import java.time.Instant

data class BillRevisionRecordCreation(
    val recordIdentity: String,
    val channelTransactionIdentity: String,
    val transactionKind: ReconciliationTransactionKind,
    val amount: BigDecimal,
    val currency: String,
    val rawStatus: String,
    val occurredAt: Instant?,
    val receivedAt: Instant,
    val rawEvidence: String
)
