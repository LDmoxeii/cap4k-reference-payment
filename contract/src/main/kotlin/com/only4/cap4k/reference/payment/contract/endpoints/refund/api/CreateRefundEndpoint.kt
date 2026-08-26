package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/refunds
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreateRefundEndpoint",
    packageName = "refund.api",
    description = "POST /api/refunds",
    aggregates = [],
    operationName = "refund.create",
    family = "endpoint"
)
object CreateRefundEndpoint {
    const val OPERATION_NAME: String = "refund.create"

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 商户退款单号
         */
        val merchantRefundNumber: String,
        /**
         * 支付标识
         */
        val paymentId: String,
        /**
         * 金额
         */
        val amount: BigDecimal,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 请求时间
         */
        val requestedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 退款标识
         */
        val refundId: String,
        /**
         * 退款尝试标识
         */
        val refundAttemptId: String,
        /**
         * 状态
         */
        val status: String,
        /**
         * 请求身份
         */
        val requestIdentity: String,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean,
        /**
         * 诊断摘要
         */
        val diagnosticSummary: String?
    )

}
