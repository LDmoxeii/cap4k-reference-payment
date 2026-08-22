package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementResultDisposition

@DesignBlockMetadata(
    tag = "value_object",
    name = "SettlementResultRecordingOutcome",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values",
    description = "Domain outcome produced after recording and adjudicating a settlement result receipt",
    aggregates = ["MerchantSettlement"],
    family = "value-object"
)
data class SettlementResultRecordingOutcome(
    val settlementStatus: MerchantSettlementStatus,
    val attemptStatus: SettlementExecutionAttemptStatus?,
    val notificationReceiveCount: Int,
    val disposition: SettlementResultDisposition,
    val rejectionSummary: String?,
    val conflictSummary: String?,
    val reviewSummary: String?,
    val settledFactFormedNow: Boolean
)
