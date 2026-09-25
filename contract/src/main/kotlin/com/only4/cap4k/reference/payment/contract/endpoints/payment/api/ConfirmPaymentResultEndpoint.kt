package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.ChannelResultDisposition
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import java.time.Instant

/**
 * POST /api/channel/payment-results
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmPaymentResultEndpoint",
    packageName = "payment.api",
    description = "POST /api/channel/payment-results",
    aggregates = [],
    operationName = "payment.result.confirm",
    family = "endpoint"
)
object ConfirmPaymentResultEndpoint {
    const val OPERATION_NAME: String = "payment.result.confirm"

    data class Request(
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 通知标识
         */
        val notificationId: String,
        /**
         * 支付标识
         */
        val paymentId: String,
        /**
         * 支付尝试标识
         */
        val paymentAttemptId: String,
        /**
         * 渠道交易标识
         */
        val channelTransactionId: String,
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
        /** callback 的 canonical raw payload。验真结论只由服务端 reference verifier 形成。 */
        val rawPayload: String? = null,
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 支付状态
         */
        val paymentStatus: String,
        /**
         * 尝试状态
         */
        val attemptStatus: String?,
        /**
         * 通知接收次数
         */
        val notificationReceiveCount: Int,
        /**
         * 处置结果
         */
        val disposition: ChannelResultDisposition,
        /**
         * 是否重复
         */
        val duplicate: Boolean,
        /**
         * 是否接受
         */
        val accepted: Boolean,
        /**
         * 是否拒绝
         */
        val rejected: Boolean,
        /**
         * 是否冲突
         */
        val conflicting: Boolean,
        /**
         * 拒绝摘要
         */
        val rejectionSummary: String?,
        /**
         * 冲突摘要
         */
        val conflictSummary: String?,
        /**
         * 当前是否形成成功事实
         */
        val successFactFormedNow: Boolean,
        /**
         * 复核身份
         */
        val reviewIdentity: String?,
        /**
         * 是否符合结算条件
         */
        val settlementEligible: Boolean,
        /**
         * 通知意图状态
         */
        val notificationIntentState: String?,
        /** Stable command acceptance record for this callback payload. */
        val receipt: OperationReceipt,
    )

}
