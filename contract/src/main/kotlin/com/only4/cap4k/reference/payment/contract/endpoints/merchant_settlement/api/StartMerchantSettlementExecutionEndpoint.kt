package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/**
 * POST /api/merchant-settlements/{settlementId}/executions
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "StartMerchantSettlementExecutionEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/merchant-settlements/{settlementId}/executions",
    aggregates = [],
    operationName = "merchant-settlement.execution.start",
    family = "endpoint"
)
object StartMerchantSettlementExecutionEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.execution.start"

    data class Request(
        /**
         * 结算标识
         */
        val settlementId: String,
        /**
         * 操作员身份
         */
        val operatorIdentity: String,
        /**
         * 操作员角色
         */
        val operatorRole: String,
        /**
         * 请求时间
         */
        val requestedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 结算标识
         */
        val settlementId: String,
        /**
         * 尝试标识
         */
        val attemptId: String?,
        /**
         * 执行组身份
         */
        val executionGroupIdentity: String?,
        /**
         * 请求身份
         */
        val requestIdentity: String?,
        /**
         * 状态
         */
        val status: String,
        /**
         * 渠道是否接受
         */
        val providerAccepted: Boolean,
        /**
         * 诊断摘要
         */
        val diagnosticSummary: String?
    )

}
