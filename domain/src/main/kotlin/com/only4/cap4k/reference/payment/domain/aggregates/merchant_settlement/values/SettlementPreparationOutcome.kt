package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
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
    /**
     * 结算标识
     */
    val merchantSettlementId: MerchantSettlementId?,
    /**
     * 状态
     */
    val status: MerchantSettlementStatus?,
    /**
     * 创建时间
     */
    val created: Boolean,
    /**
     * 是否幂等重放
     */
    val idempotentReplay: Boolean,
    /**
     * 是否无操作
     */
    val noOp: Boolean,
    /**
     * 符合数量
     */
    val eligibleCount: Int,
    /**
     * 排除数量
     */
    val excludedCount: Int,
    /**
     * 阻塞摘要
     */
    val blockerSummary: String?,
    /**
     * 支付毛金额
     */
    val paymentGrossAmount: BigDecimal,
    /**
     * 退款毛金额
     */
    val refundGrossAmount: BigDecimal,
    /**
     * 费用总额
     */
    val feeTotalAmount: BigDecimal,
    /**
     * 调整总额
     */
    val adjustmentTotalAmount: BigDecimal,
    /**
     * 净金额
     */
    val netAmount: BigDecimal
)
