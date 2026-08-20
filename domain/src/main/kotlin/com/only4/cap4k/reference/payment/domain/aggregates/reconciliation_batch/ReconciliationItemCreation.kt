package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch

import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDifferenceType
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReconciliationItemCreation(
    val differenceIdentity: String,
    val transactionKind: ReconciliationTransactionKind,
    val differenceType: ReconciliationDifferenceType,
    val channelRecordIdentity: String?,
    val channelTransactionIdentity: String?,
    val channelAmount: BigDecimal?,
    val channelCurrency: String?,
    val channelRawStatus: String?,
    val channelOccurredAt: LocalDateTime?,
    val channelReceivedAt: LocalDateTime?,
    val platformFactIdentity: String?,
    val paymentId: String?,
    val paymentAttemptId: String?,
    val refundId: String?,
    val refundAttemptId: String?,
    val platformTransactionIdentity: String?,
    val platformAmount: BigDecimal?,
    val platformCurrency: String?,
    val platformRawStatus: String?,
    val platformOccurredAt: LocalDateTime?,
    val platformRecordedAt: LocalDateTime?,
    val matchingBasis: String,
    val auxiliaryMatchApproved: Boolean = false,
    val resolved: Boolean = false,
    val settlementBlocked: Boolean = true,
    val reconciliationConfirmationFacts: List<ReconciliationConfirmationFactCreation> = emptyList(),
    val reconciliationDispositions: List<ReconciliationDispositionCreation> = emptyList()
)
