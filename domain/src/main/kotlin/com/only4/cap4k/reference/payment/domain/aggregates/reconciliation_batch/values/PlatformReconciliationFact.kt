package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "value_object",
    name = "PlatformReconciliationFact",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values",
    description = "Immutable payment or refund fact projected for reconciliation without an ORM aggregate relation",
    aggregates = ["ReconciliationBatch"],
    family = "value-object"
)
data class PlatformReconciliationFact(
    /**
     * 事实身份
     */
    val factIdentity: String,
    /**
     * 交易类型
     */
    val transactionKind: ReconciliationTransactionKind,
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
     * 记录时间
     */
    val recordedAt: Instant,
    /**
     * 复核身份快照
     */
    val paymentReviewIdentitySnapshot: String? = null,
    /**
     * 复核摘要
     */
    val paymentReviewSummary: String? = null,
    /**
     * 是否符合结算条件
     */
    val settlementEligible: Boolean = true,
)
