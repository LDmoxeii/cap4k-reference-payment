package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch

import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.DispositionAuthorization
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionConclusion
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.SettlementImpact
import java.time.LocalDateTime

data class ReconciliationDispositionCreation(
    val operatorIdentity: String,
    val operatorRole: String,
    val authorizationResult: DispositionAuthorization,
    val status: ReconciliationDispositionStatus,
    val conclusion: ReconciliationDispositionConclusion?,
    val settlementImpact: SettlementImpact,
    val evidence: String,
    val followUp: String?,
    val disposedAt: LocalDateTime
)
