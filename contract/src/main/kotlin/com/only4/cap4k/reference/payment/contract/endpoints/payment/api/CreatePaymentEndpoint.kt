package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/payments
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreatePaymentEndpoint",
    packageName = "payment.api",
    description = "POST /api/payments",
    aggregates = [],
    operationName = "payment.create",
    family = "endpoint"
)
object CreatePaymentEndpoint {
    const val OPERATION_NAME: String = "payment.create"

    data class Request(
        val merchantId: String,
        val merchantOrderNumber: String,
        val idempotencyKey: String,
        val amount: BigDecimal,
        val currency: String,
        val paymentMethod: String,
        val expiresAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        val paymentId: String,
        val status: String,
        val idempotentReplay: Boolean,
        val rejectionCode: String?,
        val rejectionSummary: String?
    )

}
