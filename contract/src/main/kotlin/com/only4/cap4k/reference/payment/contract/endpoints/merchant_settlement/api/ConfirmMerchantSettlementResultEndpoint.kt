package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/channel/settlement-results
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmMerchantSettlementResultEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/channel/settlement-results",
    aggregates = [],
    operationName = "merchant-settlement.result.confirm",
    family = "endpoint"
)
object ConfirmMerchantSettlementResultEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.result.confirm"

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
         * 结算标识
         */
        val settlementId: String,
        /**
         * 执行尝试标识
         */
        val executionAttemptId: String,
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
         * 结果代码
         */
        val resultCode: String?,
        /**
         * 发生时间
         */
        val occurredAt: Instant,
        /**
         * 接收时间
         */
        val receivedAt: Instant,
        /**
         * 核验材料
         */
        val verificationMaterial: String
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 结算状态
         */
        val settlementStatus: String,
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

}
