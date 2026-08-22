package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "value_object",
    name = "SettlementPreparationOutcome",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values",
    description = "Domain outcome of idempotently preparing a merchant settlement scope",
    aggregates = ["MerchantSettlement"],
    family = "value-object"
)
data class SettlementPreparationOutcome(
    val settlementId: String?,
    val status: MerchantSettlementStatus?,
    val created: Boolean,
    val idempotentReplay: Boolean,
    val noOp: Boolean,
    val eligibleCount: Int,
    val excludedCount: Int,
    val blockerSummary: String?,
    val paymentGrossAmount: BigDecimal,
    val refundGrossAmount: BigDecimal,
    val feeTotalAmount: BigDecimal,
    val adjustmentTotalAmount: BigDecimal,
    val netAmount: BigDecimal
)
