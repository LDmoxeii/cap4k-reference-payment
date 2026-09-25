package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt

/** POST /api/refunds/{refundId}/attempts: create, but do not submit, one refund attempt. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreateRefundAttemptEndpoint",
    packageName = "refund.api",
    description = "POST /api/refunds/{refundId}/attempts",
    aggregates = [],
    operationName = "refund.attempt.create",
    family = "endpoint",
)
object CreateRefundAttemptEndpoint {
    const val OPERATION_NAME: String = "refund.attempt.create"

    data class Request(
        val refundId: String,
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
        val reusedExistingAttempt: Boolean,
        val receipt: OperationReceipt,
    )
}
