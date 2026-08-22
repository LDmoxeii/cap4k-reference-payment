package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementLineSourceKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "value_object",
    name = "SettlementCandidateFact",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values",
    description = "Immutable current-effective-run fact eligible or reviewable for one merchant settlement scope",
    aggregates = ["MerchantSettlement"],
    family = "value-object"
)
data class SettlementCandidateFact(
    val sourceKind: SettlementLineSourceKind,
    val transactionKind: ReconciliationTransactionKind,
    val sourceFactIdentity: String,
    val feeFactIdentity: String?,
    val merchantId: String,
    val channelId: String,
    val currency: String,
    val paymentId: String?,
    val paymentAttemptId: String?,
    val refundId: String?,
    val refundAttemptId: String?,
    val reconciliationBatchId: String,
    val reconciliationRunId: String,
    val reconciliationItemId: String?,
    val reconciliationConfirmationFactId: String?,
    val externalTransactionIdentity: String,
    val grossAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val signedNetAmount: BigDecimal,
    val occurredAt: Instant,
    val recordedAt: Instant,
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
