package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/** GET /api/payments/{paymentId} */
@DesignBlockMetadata(tag = "endpoint", name = "GetPaymentEndpoint", packageName = "payment.api", description = "GET /api/payments/{paymentId}", aggregates = [], operationName = "payment.get", family = "endpoint")
object GetPaymentEndpoint {
    const val OPERATION_NAME = "payment.get"
    data class Request(
        /**
         * 支付标识
         */
        val paymentId: String
    ) : EndpointRequest<Response>
    data class Response(
        /**
         * 支付标识
         */
        val paymentId: String,
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 商户订单号
         */
        val merchantOrderNumber: String,
        /**
         * 金额
         */
        val amount: BigDecimal,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 支付方式
         */
        val paymentMethod: String,
        /**
         * 状态
         */
        val status: String,
        /**
         * 创建时间
         */
        val createdAt: Instant,
        /**
         * 过期时间
         */
        val expiresAt: Instant,
        /**
         * 成功时间
         */
        val succeededAt: Instant?,
        /**
         * 关闭时间
         */
        val closedAt: Instant?,
        /**
         * 关闭原因
         */
        val closeReason: String?,
        /**
         * 渠道交易标识
         */
        val channelTransactionId: String?,
        val reservedRefundAmount: BigDecimal,
        val successfulRefundAmount: BigDecimal,
        val refundableAmount: BigDecimal,
        /**
         * 尝试次数
         */
        val attemptCount: Int,
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
         * 是否形成成功事实
         */
        val successFactFormed: Boolean,
        /**
         * 商户订单成功身份
         */
        val merchantOrderSuccessIdentity: String?,
        /**
         * 商户成功通知意图数量
         */
        val merchantSuccessNotificationIntentCount: Int,
        /**
         * 商户成功通知意图身份
         */
        val merchantSuccessNotificationIntentIdentity: String?,
        /**
         * 商户成功通知意图状态
         */
        val merchantSuccessNotificationIntentState: String?,
        /**
         * 复核次数
         */
        val reviewCount: Int,
        /**
         * 阻塞复核数量
         */
        val blockingReviewCount: Int,
        /**
         * 是否符合结算条件
         */
        val settlementEligible: Boolean,
        /**
         * 是否阻塞结算
         */
        val settlementBlocked: Boolean,
        /**
         * 尝试列表
         */
        val attempts: List<PaymentAttemptSummary>,
        /**
         * 复核列表
         */
        val reviews: List<PaymentReviewSummary>,
    ) {
        data class PaymentAttemptSummary(
            /**
             * 支付尝试标识
             */
            val paymentAttemptId: String,
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
             * 渠道交易标识
             */
            val channelTransactionId: String?,
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
             * 尝试列表通知首次接收
             */
            val notificationFirstReceivedAt: Instant?,
            /**
             * 尝试列表通知最近接收
             */
            val notificationLastReceivedAt: Instant?,
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
            val notificationReceipts: List<NotificationReceiptSummary>,
        )
        data class NotificationReceiptSummary(
            /**
             * 通知身份
             */
            val notificationIdentity: String,
            /**
             * 载荷身份
             */
            val payloadIdentity: String,
            /**
             * 渠道标识
             */
            val channelId: String,
            /**
             * 渠道交易标识
             */
            val channelTransactionId: String,
            /**
             * 金额
             */
            val amount: BigDecimal,
            /**
             * 币种
             */
            val currency: String,
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
            val conflictSummary: String?,
        )
        data class PaymentReviewSummary(
            /**
             * 复核列表复核标识
             */
            val reviewId: String,
            /**
             * 复核身份
             */
            val reviewIdentity: String,
            /**
             * 复核列表类型
             */
            val type: String,
            /**
             * 状态
             */
            val status: String,
            /**
             * 复核列表打开
             */
            val openedAt: Instant,
            /**
             * 复核列表摘要
             */
            val summary: String,
            /**
             * 结算影响
             */
            val settlementImpact: String,
            /**
             * 复核列表解决
             */
            val resolvedAt: Instant?,
            /**
             * 复核列表尝试
             */
            val triggeringAttemptIdentities: String?,
            /**
             * 复核列表回执
             */
            val triggeringReceiptIdentities: String?,
            /**
             * 复核决策列表
             */
            val decisions: List<PaymentReviewDecisionSummary>,
        )
        data class PaymentReviewDecisionSummary(
            /**
             * 决策标识
             */
            val decisionId: String,
            /**
             * 决策身份
             */
            val decisionIdentity: String,
            /**
             * 决策
             */
            val decision: String,
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
            val authorizationOutcome: Boolean,
            /**
             * 原因
             */
            val reason: String,
            /**
             * 证据
             */
            val evidence: String,
            /**
             * 决策时间
             */
            val decidedAt: Instant,
            /**
             * 资格影响
             */
            val eligibilityImpact: String,
            /**
             * 补救引用
             */
            val remediationReference: String?,
        )
    }
}
