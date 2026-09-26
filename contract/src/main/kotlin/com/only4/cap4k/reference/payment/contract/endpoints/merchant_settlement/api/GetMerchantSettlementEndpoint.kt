package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant

/**
 * GET /api/merchant-settlements/{settlementId}
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetMerchantSettlementEndpoint",
    packageName = "merchant_settlement.api",
    description = "GET /api/merchant-settlements/{settlementId}",
    aggregates = [],
    operationName = "merchant-settlement.get",
    family = "endpoint"
)
object GetMerchantSettlementEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.get"

    data class Request(
        /**
         * 结算标识
         */
        val settlementId: String
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 结算标识
         */
        val settlementId: String,
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 执行渠道快照；不属于 scope
         */
        val executionChannelId: String?,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 期间类型
         */
        val periodType: String,
        /**
         * 期间开始日期
         */
        val periodStart: Instant,
        /**
         * 期间结束日期
         */
        val periodEnd: Instant,
        /**
         * 业务时区
         */
        val businessTimezone: String,
        /**
         * 范围身份
         */
        val scopeIdentity: String,
        /**
         * 生效范围身份
         */
        val effectiveScopeIdentity: String?,
        /**
         * Unified public settlement status.
         */
        val status: String,
        /** Settlement finality is independent from command/operation completion. */
        val finality: Finality,
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
        val grossMoney: Money,
        /**
         * 退款毛金额
         */
        val refundMoney: Money,
        /**
         * 费用总额
         */
        val feeMoney: Money,
        /**
         * 调整总额
         */
        val adjustmentMoney: Money,
        /**
         * 净金额
         */
        val netMoney: Money,
        /**
         * 是否冻结组合
         */
        val compositionFrozen: Boolean,
        /**
         * 执行组身份
         */
        val executionGroupIdentity: String?,
        /**
         * 前序结算标识
         */
        val predecessorSettlementId: String?,
        /**
         * 替代结算标识
         */
        val replacementSettlementId: String?,
        /**
         * 确认操作人
         */
        val confirmedBy: String?,
        /**
         * 确认时间
         */
        val confirmedAt: Instant?,
        val confirmedReason: String?,
        val confirmedEvidence: String?,
        /**
         * 作废操作人
         */
        val voidedBy: String?,
        /**
         * 作废原因
         */
        val voidReason: String?,
        val voidEvidence: String?,
        /**
         * 作废时间
         */
        val voidedAt: Instant?,
        /**
         * 是否形成结算事实
         */
        val settledFactFormed: Boolean,
        /**
         * 外部结算身份
         */
        val externalSettlementIdentity: String?,
        /**
         * 完成时间
         */
        val completedAt: Instant?,
        /**
         * 最近摘要
         */
        val lastRejectionSummary: String?,
        /**
         * 最近摘要
         */
        val lastConflictSummary: String?,
        /**
         * 最近复核摘要
         */
        val lastReviewSummary: String?,
        /**
         * 明细行列表
         */
        val lines: List<SettlementLineSummary>,
        /**
         * 尝试列表
         */
        val attempts: List<SettlementExecutionAttemptSummary>
    ) {
        data class SettlementLineSummary(
            /**
             * 行标识
             */
            val lineId: String,
            /**
             * 明细行列表行身份
             */
            val lineIdentity: String,
            /**
             * 来源类型
             */
            val sourceKind: String,
            /**
             * 交易类型
             */
            val transactionKind: String,
            /**
             * 来源事实身份
             */
            val sourceFactIdentity: String,
            val decision: String,
            val reasonCode: String,
            /**
             * 费用事实身份
             */
            val feeFactIdentity: String?,
            /**
             * 支付标识
             */
            val paymentId: String?,
            /**
             * 支付尝试标识
             */
            val paymentAttemptId: String?,
            /**
             * 退款标识
             */
            val refundId: String?,
            /**
             * 退款尝试标识
             */
            val refundAttemptId: String?,
            /**
             * 对账批次标识
             */
            val reconciliationBatchId: String?,
            /**
             * 对账运行标识
             */
            val reconciliationRunId: String?,
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
            val grossMoney: Money,
            /**
             * 费用金额
             */
            val feeMoney: Money,
            /**
             * 带符号净额
             */
            val signedNetMoney: Money,
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
            val feeFixedMoney: Money?,
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
            val feeCalculationMoney: Money?,
            /**
             * 明细行列表依据
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
        data class SettlementExecutionAttemptSummary(
            val executionId: String,
            val idempotencyKey: String,
            val executorScript: String,
            val executorObservation: String,
            val diagnosticSummary: String?,
            /**
             * 尝试列表尝试标识
             */
            val attemptId: String,
            /**
             * 尝试列表尝试
             */
            val attemptSequence: Int,
            /**
             * 执行组身份
             */
            val executionGroupIdentity: String,
            /**
             * 请求身份
             */
            val requestIdentity: String,
            /**
             * 渠道标识
             */
            val channelId: String,
            /**
             * 状态
             */
            val status: String,
            /**
             * 发起时间
             */
            val initiatedAt: Instant,
            /**
             * 尝试列表接受
             */
            val acceptedAt: Instant?,
            /**
             * 尝试列表复核后
             */
            val reviewAfterMinutesSnapshot: Int,
            /**
             * 复核时间
             */
            val reviewAfterAt: Instant,
            /**
             * 金额
             */
            val money: Money,
            /**
             * 外部结算身份
             */
            val externalSettlementIdentity: String?,
            /**
             * 最终结果
             */
            val finalResult: String?,
            /**
             * 结果发生时间
             */
            val resultOccurredAt: Instant?,
            /**
             * 通知接收次数
             */
            val notificationReceiveCount: Int,
            /**
             * 拒绝摘要
             */
            val rejectionSummary: String?,
            /**
             * 冲突摘要
             */
            val conflictSummary: String?,
            /**
             * 尝试回执列表
             */
            val receipts: List<SettlementResultReceiptSummary>
        )
        data class SettlementResultReceiptSummary(
            val executionId: String,
            /**
             * 尝试列表回执列表回执标识
             */
            val receiptId: String,
            /**
             * 通知身份
             */
            val notificationIdentity: String,
            /**
             * 载荷指纹
             */
            val payloadFingerprint: String,
            /**
             * 渠道标识
             */
            val channelId: String,
            /**
             * 执行组身份
             */
            val executionGroupIdentity: String,
            /**
             * 请求身份
             */
            val requestIdentity: String,
            /**
             * 外部结算身份
             */
            val externalSettlementIdentity: String,
            /**
             * 金额
             */
            val money: Money,
            /**
             * 结果
             */
            val result: String,
            /**
             * 尝试列表回执列表结果代码
             */
            val resultCode: String?,
            /**
             * 发生时间
             */
            val occurredAt: Instant,
            /**
             * 尝试列表回执列表首次接收
             */
            val firstReceivedAt: Instant,
            /**
             * 尝试列表回执列表最近接收
             */
            val lastReceivedAt: Instant,
            /**
             * 尝试列表回执列表数量
             */
            val receiveCount: Int,
            /**
             * 是否核验通过
             */
            val verified: Boolean,
            /**
             * 是否接受
             */
            val accepted: Boolean,
            /**
             * 决策
             */
            val decision: String,
            /**
             * 判定摘要
             */
            val verdictSummary: String?,
            /**
             * 拒绝摘要
             */
            val rejectionSummary: String?,
            /**
             * 冲突摘要
             */
            val conflictSummary: String?
        )
    }

}
