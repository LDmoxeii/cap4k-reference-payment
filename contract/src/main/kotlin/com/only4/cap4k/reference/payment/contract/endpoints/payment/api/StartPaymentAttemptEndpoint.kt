package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest

/**
 * POST /api/payments/{paymentId}/attempts
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "StartPaymentAttemptEndpoint",
    packageName = "payment.api",
    description = "POST /api/payments/{paymentId}/attempts",
    aggregates = [],
    operationName = "payment.attempt.start",
    family = "endpoint"
)
object StartPaymentAttemptEndpoint {
    const val OPERATION_NAME: String = "payment.attempt.start"

    data class Request(
        /**
         * 支付标识
         */
                val paymentId: String
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 支付尝试标识
         */
                val paymentAttemptId: String,
        /**
         * 渠道标识
         */
                val channelId: String,
        /**
         * 请求身份
         */
                val requestIdentity: String,
        /**
         * 支付状态
         */
                val paymentStatus: String,
        /**
         * 尝试状态
         */
                val attemptStatus: String
    )

}
