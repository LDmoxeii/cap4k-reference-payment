package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementLineSourceKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
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
    /**
     * 来源类型
     */
    val sourceKind: SettlementLineSourceKind,
    /**
     * 交易类型
     */
    val transactionKind: ReconciliationTransactionKind,
    /**
     * 来源事实身份
     */
    val sourceFactIdentity: String,
    /**
     * 费用事实身份
     */
    val feeFactIdentity: String?,
    /**
     * 商户标识
     */
    val merchantId: String,
    /**
     * 渠道标识
     */
    val channelId: String,
    /**
     * 币种
     */
    val currency: String,
    /**
     * 支付标识
     */
    val paymentId: PaymentId?,
    /**
     * 支付尝试标识
     */
    val paymentAttemptId: String?,
    /**
     * 退款标识
     */
    val refundId: RefundId?,
    /**
     * 退款尝试标识
     */
    val refundAttemptId: String?,
    /**
     * 对账批次标识
     */
    val reconciliationBatchId: ReconciliationBatchId,
    /**
     * 对账运行标识
     */
    val reconciliationRunId: String,
    /**
     * 对账条目标识
     */
    val reconciliationItemId: String?,
    /**
     * 对账确认事实标识
     */
    val reconciliationConfirmationFactId: String?,
    /**
     * 外部交易身份
     */
    val externalTransactionIdentity: String,
    /**
     * 毛金额
     */
    val grossAmount: BigDecimal,
    /**
     * 费用金额
     */
    val feeAmount: BigDecimal,
    /**
     * 带符号净额
     */
    val signedNetAmount: BigDecimal,
    /**
     * 发生时间
     */
    val occurredAt: Instant,
    /**
     * 记录时间
     */
    val recordedAt: Instant,
    /**
     * 费率基点
     */
    val feeBasisPoints: Int?,
    /**
     * 固定费用
     */
    val feeFixedAmount: BigDecimal?,
    /**
     * 费用舍入方式
     */
    val feeRoundingMode: String?,
    /**
     * 货币精度
     */
    val feeCurrencyPrecision: Int?,
    /**
     * 费用计算金额
     */
    val feeCalculationAmount: BigDecimal?,
    /**
     * 依据
     */
    val eligibilityBasis: String,
    /**
     * 确认原因
     */
    val confirmationReason: String?,
    /**
     * 确认证据
     */
    val confirmationEvidence: String?,
    /**
     * 调整来源身份
     */
    val adjustmentSourceIdentity: String?,
    /**
     * 调整证据
     */
    val adjustmentEvidence: String?
)
