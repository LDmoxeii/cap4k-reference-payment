package com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill

import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import java.time.Instant

data class BillRevisionCreation(
    val revision: String,
    val completeness: StatementCompleteness,
    val rawEvidence: String,
    val payloadFingerprint: String,
    val publishedAt: Instant,
    val records: List<BillRevisionRecordCreation> = emptyList()
)
