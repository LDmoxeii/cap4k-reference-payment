package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/channel/refund-results
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmRefundResultEndpoint",
    packageName = "refund.api",
    description = "POST /api/channel/refund-results",
    aggregates = [],
    operationName = "refund.result.confirm",
    family = "endpoint"
)
object ConfirmRefundResultEndpoint {
    const val OPERATION_NAME: String = "refund.result.confirm"

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
         * 退款标识
         */
        val refundId: String,
        /**
         * 退款尝试标识
         */
        val refundAttemptId: String,
        /**
         * 渠道退款标识
         */
        val channelRefundId: String,
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
         * 核验材料
         */
        val verificationMaterial: String
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 退款状态
         */
        val refundStatus: String,
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
        val disposition: String,
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
         * 当前是否释放预留
         */
        val reservationReleasedNow: Boolean,
        /**
         * 当前是否转为成功
         */
        val reservationConvertedToSuccessNow: Boolean,
        /**
         * 当前是否需要复核
         */
        val reviewRequiredNow: Boolean,
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
