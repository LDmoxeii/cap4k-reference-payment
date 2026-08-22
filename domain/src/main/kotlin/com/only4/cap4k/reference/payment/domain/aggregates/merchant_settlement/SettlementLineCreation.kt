package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementLineSourceKind
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.math.BigDecimal
import java.time.LocalDateTime

data class SettlementLineCreation(
    val lineIdentity: String,
    val sourceKind: SettlementLineSourceKind,
    val transactionKind: ReconciliationTransactionKind,
    val sourceFactIdentity: String,
    val effectiveConsumptionIdentity: String?,
    val feeFactIdentity: String?,
    val paymentId: PaymentId?,
    val paymentAttemptId: String?,
    val refundId: RefundId?,
    val refundAttemptId: String?,
    val reconciliationBatchId: ReconciliationBatchId?,
    val reconciliationRunId: String?,
    val reconciliationItemId: String?,
    val reconciliationConfirmationFactId: String?,
    val externalTransactionIdentity: String,
    val grossAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val signedNetAmount: BigDecimal,
    val currency: String,
    val occurredAt: LocalDateTime,
    val recordedAt: LocalDateTime,
    val feeBasisPoints: Int?,
    val feeFixedAmount: BigDecimal?,
    val feeRoundingMode: String?,
    val feeCurrencyPrecision: Int?,
    val feeCalculationAmount: BigDecimal?,
    val eligibilityBasis: String,
    val confirmationReason: String?,
    val confirmationEvidence: String?,
    val adjustmentSourceIdentity: String?,
    val adjustmentEvidence: String?
)
