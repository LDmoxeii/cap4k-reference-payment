
package com.only4.cap4k.reference.payment.application.queries.reconciliation.read

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.Query
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@DesignBlockMetadata(
    tag = "query",
    name = "GetReconciliationBatch",
    packageName = "reconciliation.read",
    description = "Read a reconciliation batch with immutable run, item, disposition, and confirmation evidence",
    aggregates = ["ReconciliationBatch"],
    family = "query"
)
object GetReconciliationBatchQry {

    data class Request(
        /**
         * 批次标识
         */
        val batchId: String
    ) : Query<Response>

    data class Response(
        /**
         * 批次标识
         */
        val batchId: String,
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 对账日期
         */
        val reconciliationDate: LocalDate,
        /**
         * 业务时区
         */
        val businessTimezone: String,
        /**
         * 状态
         */
        val status: String,
        /**
         * 当前生效运行标识
         */
        val currentEffectiveRunId: String?,
        /**
         * 对账单等待截止时间
         */
        val statementWaitDeadlineAt: Instant,
        /**
         * 匹配数量
         */
        val matchedCount: Int,
        /**
         * 差异数量
         */
        val differenceCount: Int,
        /**
         * 未解决差异数量
         */
        val unresolvedDifferenceCount: Int,
        /**
         * 是否阻塞结算
         */
        val settlementBlocked: Boolean,
        /**
         * 原因
         */
        val blockingReason: String?,
        /**
         * 完成时间
         */
        val completedAt: Instant?,
        /**
         * 运行列表
         */
        val runs: List<ReconciliationRunSummary>
    ) {
        data class ReconciliationRunSummary(
            /**
             * 运行标识
             */
            val runId: String,
            /**
             * 对账单身份
             */
            val statementIdentity: String,
            /**
             * 对账单版本
             */
            val statementRevision: String,
            /**
             * 对账单完整性
             */
            val statementCompleteness: String,
            /**
             * 状态
             */
            val status: String,
            /**
             * 抓取时间
             */
            val fetchedAt: Instant,
            /**
             * 开始时间
             */
            val startedAt: Instant,
            /**
             * 完成时间
             */
            val completedAt: Instant?,
            /**
             * 渠道记录数量
             */
            val channelRecordCount: Int,
            /**
             * 平台事实数量
             */
            val platformFactCount: Int,
            /**
             * 匹配数量
             */
            val matchedCount: Int,
            /**
             * 差异数量
             */
            val differenceCount: Int,
            /**
             * 未解决差异数量
             */
            val unresolvedDifferenceCount: Int,
            /**
             * 失败摘要
             */
            val failureSummary: String?,
            /**
             * 运行条目列表
             */
            val items: List<ReconciliationItemSummary>
        )
        data class ReconciliationItemSummary(
            /**
             * 条目标识
             */
            val itemId: String,
            /**
             * 运行列表条目列表差异身份
             */
            val differenceIdentity: String,
            /**
             * 交易类型
             */
            val transactionKind: String,
            /**
             * 差异类型
             */
            val differenceType: String,
            /**
             * 运行列表条目列表渠道记录身份
             */
            val channelRecordIdentity: String?,
            /**
             * 渠道交易身份
             */
            val channelTransactionIdentity: String?,
            /**
             * 运行列表条目列表渠道金额
             */
            val channelAmount: BigDecimal?,
            /**
             * 运行列表条目列表渠道币种
             */
            val channelCurrency: String?,
            /**
             * 运行列表条目列表渠道原始状态
             */
            val channelRawStatus: String?,
            /**
             * 运行列表条目列表渠道发生
             */
            val channelOccurredAt: Instant?,
            /**
             * 运行列表条目列表渠道接收
             */
            val channelReceivedAt: Instant?,
            /**
             * 运行列表条目列表平台事实身份
             */
            val platformFactIdentity: String?,
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
             * 运行列表条目列表平台交易身份
             */
            val platformTransactionIdentity: String?,
            /**
             * 运行列表条目列表平台金额
             */
            val platformAmount: BigDecimal?,
            /**
             * 运行列表条目列表平台币种
             */
            val platformCurrency: String?,
            /**
             * 运行列表条目列表平台原始状态
             */
            val platformRawStatus: String?,
            /**
             * 运行列表条目列表平台发生
             */
            val platformOccurredAt: Instant?,
            /**
             * 运行列表条目列表平台记录
             */
            val platformRecordedAt: Instant?,
            val paymentReviewIdentitySnapshot: String?,
            val paymentReviewSummary: String?,
            /**
             * 匹配依据
             */
            val matchingBasis: String,
            /**
             * 是否批准辅助匹配
             */
            val auxiliaryMatchApproved: Boolean,
            /**
             * 是否已解决
             */
            val resolved: Boolean,
            /**
             * 是否阻塞结算
             */
            val settlementBlocked: Boolean,
            /**
             * 处置列表
             */
            val dispositions: List<ReconciliationDispositionSummary>,
            /**
             * 确认事实列表
             */
            val confirmationFacts: List<ReconciliationConfirmationFactSummary>
        )
        data class ReconciliationDispositionSummary(
            /**
             * 处置标识
             */
            val dispositionId: String,
            /**
             * 操作员身份
             */
            val operatorIdentity: String,
            /**
             * 操作员角色
             */
            val operatorRole: String,
            /**
             * 授权结果
             */
            val authorization: String,
            /**
             * 状态
             */
            val status: String,
            /**
             * 结论
             */
            val conclusion: String?,
            /**
             * 结算影响
             */
            val settlementImpact: String,
            /**
             * 证据
             */
            val evidence: String,
            /**
             * 后续动作
             */
            val followUp: String?,
            /**
             * 处置时间
             */
            val disposedAt: Instant
        )
        data class ReconciliationConfirmationFactSummary(
            /**
             * 确认事实标识
             */
            val confirmationFactId: String,
            /**
             * 来源差异身份
             */
            val sourceDifferenceIdentity: String,
            /**
             * 操作员身份
             */
            val operatorIdentity: String,
            /**
             * 确认原因
             */
            val confirmationReason: String,
            /**
             * 证据
             */
            val evidence: String,
            /**
             * 交易类型
             */
            val transactionKind: String,
            /**
             * 金额
             */
            val amount: BigDecimal,
            /**
             * 币种
             */
            val currency: String,
            /**
             * 外部交易身份
             */
            val externalTransactionIdentity: String,
            /**
             * 支付标识
             */
            val paymentId: String?,
            /**
             * 退款标识
             */
            val refundId: String?,
            /**
             * 确认时间
             */
            val confirmedAt: Instant
        )
    }

}
