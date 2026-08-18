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
        val paymentId: String
    ) : EndpointRequest<Response>

    data class Response(
        val paymentAttemptId: String,
        val channelId: String,
        val requestIdentity: String,
        val paymentStatus: String,
        val attemptStatus: String
    )

}
