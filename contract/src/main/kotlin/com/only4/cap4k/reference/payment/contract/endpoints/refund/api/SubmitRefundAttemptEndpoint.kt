package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt

/** POST /api/refunds/{refundId}/attempts/{refundAttemptId}/submissions: submit a created attempt. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "SubmitRefundAttemptEndpoint",
    packageName = "refund.api",
    description = "POST /api/refunds/{refundId}/attempts/{refundAttemptId}/submissions",
    aggregates = [],
    operationName = "refund.attempt.submit",
    family = "endpoint",
)
object SubmitRefundAttemptEndpoint {
    const val OPERATION_NAME: String = "refund.attempt.submit"

    data class Request(
        val refundId: String,
        val refundAttemptId: String,
        val idempotencyKey: String,
    ) : EndpointRequest<Response>

    data class Response(
        val refundId: String,
        val refundAttemptId: String,
        val refundStatus: String,
        val finality: Finality,
        val attemptStatus: String,
        val channelId: String,
        val requestIdentity: String,
        val diagnosticSummary: String?,
        val receipt: OperationReceipt,
    )
}
