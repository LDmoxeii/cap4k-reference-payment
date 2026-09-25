package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.ChannelResultDisposition
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant

/**
 * GET /api/refunds/{refundId}
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetRefundEndpoint",
    packageName = "refund.api",
    description = "GET /api/refunds/{refundId}",
    aggregates = [],
    operationName = "refund.get",
    family = "endpoint"
)
object GetRefundEndpoint {
    const val OPERATION_NAME: String = "refund.get"

    data class Request(
        /**
         * 退款标识
         */
        val refundId: String
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 退款标识
         */
        val refundId: String,
        /**
         * 支付标识
         */
        val paymentId: String,
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 商户退款单号
         */
        val merchantRefundNumber: String,
        /**
         * 退款申请幂等键
         */
        val idempotencyKey: String,
        /**
         * 金额
         */
        val money: Money,
        /**
         * 退款申请原因
         */
        val reason: String,
        /**
         * 支付方式
         */
        val paymentMethod: String,
        /**
         * 状态
         */
        val status: String,
        /** Whether the refund can still advance automatically. */
        val finality: Finality,
        /**
         * 请求时间
         */
        val requestedAt: Instant,
        /**
         * 退款截止时间
         */
        val refundDeadlineAt: Instant,
        /**
         * 渠道接受
         */
        val channelAcceptedAt: Instant?,
        /**
         * 完成时间
         */
        val finalizedAt: Instant?,
        /**
         * 需要复核时间
         */
        val reviewRequiredAt: Instant?,
        /**
         * 渠道标识
         */
        val channelId: String?,
        /**
         * 渠道配置标识
         */
        val channelConfigurationId: String?,
        /**
         * 渠道
         */
        val channelConfigurationSnapshot: String?,
        /**
         * 请求身份
         */
        val requestIdentity: String?,
        /**
         * 渠道退款标识
         */
        val channelRefundId: String?,
        /**
         * 预留是否生效
         */
        val reservationActive: Boolean,
        /**
         * 预留是否释放
         */
        val reservationReleased: Boolean,
        /**
         * 预留是否转为成功
         */
        val reservationConvertedToSuccess: Boolean,
        /**
         * 是否形成成功事实
         */
        val successFactFormed: Boolean,
        /**
         * 通知接收次数
         */
        val notificationReceiveCount: Int,
        /**
         * 拒绝通知数量
         */
        val rejectedNotificationCount: Int,
        /**
         * 冲突通知数量
         */
        val conflictingNotificationCount: Int,
        /**
         * 最近通知身份
         */
        val lastNotificationIdentity: String?,
        /**
         * 最近通知接收
         */
        val lastNotificationReceivedAt: Instant?,
        /**
         * 最近摘要
         */
        val lastRejectionSummary: String?,
        /**
         * 最近摘要
         */
        val lastConflictSummary: String?,
        /**
         * 是否阻塞结算
         */
        val settlementBlocked: Boolean,
        /**
         * 尝试列表
         */
        val attempts: List<RefundAttemptSummary>
    ) {
        data class RefundAttemptSummary(
            /**
             * 退款尝试标识
             */
            val refundAttemptId: String,
            /**
             * 渠道标识
             */
            val channelId: String,
            /**
             * 状态
             */
            val status: String,
            /**
             * 请求身份
             */
            val requestIdentity: String,
            /**
             * 发起时间
             */
            val initiatedAt: Instant,
            /**
             * 尝试列表接受
             */
            val acceptedAt: Instant?,
            /**
             * 复核时间
             */
            val reviewAfterAt: Instant,
            /**
             * 渠道退款标识
             */
            val channelRefundId: String?,
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
             * 已核验通知数量
             */
            val verifiedNotificationCount: Int,
            /**
             * 拒绝通知数量
             */
            val rejectedNotificationCount: Int,
            /**
             * 冲突通知数量
             */
            val conflictingNotificationCount: Int,
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
            val conflictSummary: String?,
            /**
             * 通知回执列表
             */
            val notificationReceipts: List<RefundNotificationReceiptSummary>
        )
        data class RefundNotificationReceiptSummary(
            /**
             * 通知身份
             */
            val notificationIdentity: String,
            /**
             * 渠道标识
             */
            val channelId: String,
            /**
             * 渠道退款标识
             */
            val channelRefundId: String,
            /**
             * 金额
             */
            val money: Money,
            /**
             * 结果
             */
            val result: String,
            /**
             * 发生时间
             */
            val occurredAt: Instant,
            /**
             * 尝试列表通知回执列表首次接收
             */
            val firstReceivedAt: Instant,
            /**
             * 尝试列表通知回执列表最近接收
             */
            val lastReceivedAt: Instant,
            /**
             * 尝试列表通知回执列表数量
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
            val decision: ChannelResultDisposition,
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
