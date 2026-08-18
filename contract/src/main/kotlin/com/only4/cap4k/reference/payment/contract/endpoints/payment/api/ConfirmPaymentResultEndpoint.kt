package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/channel/payment-results
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmPaymentResultEndpoint",
    packageName = "payment.api",
    description = "POST /api/channel/payment-results",
    aggregates = [],
    operationName = "payment.result.confirm",
    family = "endpoint"
)
object ConfirmPaymentResultEndpoint {
    const val OPERATION_NAME: String = "payment.result.confirm"

    data class Request(
        val channelId: String,
        val notificationId: String,
        val paymentId: String,
        val paymentAttemptId: String,
        val channelTransactionId: String,
        val amount: BigDecimal,
        val currency: String,
        val result: String,
        val occurredAt: Instant,
        val verificationMaterial: String
    ) : EndpointRequest<Response>

    data class Response(
        val paymentStatus: String,
        val attemptStatus: String?,
        val notificationReceiveCount: Int,
        val disposition: String,
        val duplicate: Boolean,
        val accepted: Boolean,
        val rejected: Boolean,
        val conflicting: Boolean,
        val rejectionSummary: String?,
        val conflictSummary: String?,
        val successFactFormedNow: Boolean
    )

}
