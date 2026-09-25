package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt

@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreatePaymentAttemptEndpoint",
    packageName = "payment.api",
    description = "POST /api/payments/{paymentId}/attempts",
    aggregates = [],
    operationName = "payment.attempt.create",
    family = "endpoint",
)
object CreatePaymentAttemptEndpoint {
    const val OPERATION_NAME = "payment.attempt.create"

    data class Request(
        val paymentId: String = "",
        val idempotencyKey: String,
        val riskReason: String? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val paymentId: String,
        val paymentAttemptId: String,
        val paymentStatus: String,
        val attemptStatus: String,
        val channelId: String,
        val requestIdentity: String,
        val interactionInformation: String,
        val riskReason: String?,
        val receipt: OperationReceipt,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "SubmitPaymentAttemptEndpoint",
    packageName = "payment.api",
    description = "POST /api/payments/{paymentId}/attempts/{paymentAttemptId}/submissions",
    aggregates = [],
    operationName = "payment.attempt.submit",
    family = "endpoint",
)
object SubmitPaymentAttemptEndpoint {
    const val OPERATION_NAME = "payment.attempt.submit"

    data class Request(
        val paymentId: String = "",
        val paymentAttemptId: String = "",
        val idempotencyKey: String,
    ) : EndpointRequest<Response>

    data class Response(
        val paymentId: String,
        val paymentAttemptId: String,
        val paymentStatus: String,
        val attemptStatus: String,
        val submissionIdentity: String,
        val channelId: String,
        val interactionInformation: String,
        val submissionOutcome: String,
        val channelReference: String?,
        val diagnosticSummary: String?,
        val receipt: OperationReceipt,
    )
}
