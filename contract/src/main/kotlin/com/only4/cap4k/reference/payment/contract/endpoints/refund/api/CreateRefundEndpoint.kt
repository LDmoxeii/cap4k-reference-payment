package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.contract.common.Finality

/** POST /api/refunds: accept a refund request and reserve budget; it never creates a channel attempt. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "RequestRefundEndpoint",
    packageName = "refund.api",
    description = "POST /api/refunds",
    aggregates = [],
    operationName = "refund.request",
    family = "endpoint",
)
object RequestRefundEndpoint {
    const val OPERATION_NAME: String = "refund.request"

    data class Request(
        val merchantId: String,
        val idempotencyKey: String,
        val merchantRefundNo: String,
        val paymentId: String,
        val money: Money,
        val reason: String,
    ) : EndpointRequest<Response>

    data class Response(
        val refundId: String,
        val status: String,
        val finality: Finality,
        val idempotentReplay: Boolean,
        val receipt: OperationReceipt,
    )
}
