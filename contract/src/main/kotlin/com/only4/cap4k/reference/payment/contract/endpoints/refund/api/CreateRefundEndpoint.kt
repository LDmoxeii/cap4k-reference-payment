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
        val merchantId: String,
        val merchantRefundNumber: String,
        val paymentId: String,
        val amount: BigDecimal,
        val currency: String,
        val requestedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        val refundId: String,
        val refundAttemptId: String,
        val status: String,
        val requestIdentity: String,
        val idempotentReplay: Boolean,
        val diagnosticSummary: String?
    )

}
