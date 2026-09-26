package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
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
        /** Caller-supplied stable execution identity. */
        val executionId: String,
        /** Internal attempt id retained only as optional compatibility diagnostics. */
        val executionAttemptId: String? = null,
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
        /** callback 的 canonical raw payload。验真结论只由服务端 reference verifier 形成。 */
        val rawPayload: String? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val executionId: String,
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
        val settledFactFormedNow: Boolean,
        /** Stable command acceptance record for this callback payload. */
        val receipt: OperationReceipt,
    )

}
