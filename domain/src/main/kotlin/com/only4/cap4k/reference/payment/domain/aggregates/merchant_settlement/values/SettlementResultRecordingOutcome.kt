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
    /**
     * 结算状态
     */
    val settlementStatus: MerchantSettlementStatus,
    /**
     * 尝试状态
     */
    val attemptStatus: SettlementExecutionAttemptStatus?,
    /**
     * 通知接收次数
     */
    val notificationReceiveCount: Int,
    /**
     * 处置结果
     */
    val disposition: SettlementResultDisposition,
    /**
     * 拒绝摘要
     */
    val rejectionSummary: String?,
    /**
     * 冲突摘要
     */
    val conflictSummary: String?,
    /**
     * 复核摘要
     */
    val reviewSummary: String?,
    /**
     * 当前是否形成结算事实
     */
    val settledFactFormedNow: Boolean
)
