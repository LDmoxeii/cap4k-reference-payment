package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "value_object",
    name = "ChannelStatementRecord",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values",
    description = "Immutable channel statement record used for deterministic reconciliation matching",
    aggregates = ["ReconciliationBatch"],
    family = "value-object"
)
data class ChannelStatementRecord(
    /**
     * 记录身份
     */
    val recordIdentity: String,
    /**
     * 交易类型
     */
    val transactionKind: ReconciliationTransactionKind,
    /**
     * 渠道交易身份
     */
    val channelTransactionIdentity: String,
    /**
     * 金额
     */
    val amount: BigDecimal,
    /**
     * 币种
     */
    val currency: String,
    /**
     * 渠道原始状态
     */
    val rawStatus: String,
    /**
     * 发生时间
     */
    val occurredAt: Instant,
    /**
     * 接收时间
     */
    val receivedAt: Instant
)
