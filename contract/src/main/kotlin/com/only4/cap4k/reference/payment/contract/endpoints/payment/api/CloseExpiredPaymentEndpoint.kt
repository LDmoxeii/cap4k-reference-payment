package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt

/** POST /api/payments/{paymentId}/close-expired */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "CloseExpiredPaymentEndpoint",
    packageName = "payment.api",
    description = "POST /api/payments/{paymentId}/close-expired",
    aggregates = [],
    operationName = "payment.expiry.close",
    family = "endpoint",
)
object CloseExpiredPaymentEndpoint {
    const val OPERATION_NAME = "payment.expiry.close"

    data class Request(
        val paymentId: String,
        val merchantId: String,
        val idempotencyKey: String,
    ) : EndpointRequest<Response>

    data class Response(
        val paymentId: String,
        val paymentStatus: String,
        val receipt: OperationReceipt,
    )
}
