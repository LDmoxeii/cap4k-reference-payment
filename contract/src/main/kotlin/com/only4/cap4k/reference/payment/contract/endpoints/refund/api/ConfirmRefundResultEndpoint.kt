package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/channel/refund-results
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmRefundResultEndpoint",
    packageName = "refund.api",
    description = "POST /api/channel/refund-results",
    aggregates = [],
    operationName = "refund.result.confirm",
    family = "endpoint"
)
object ConfirmRefundResultEndpoint {
    const val OPERATION_NAME: String = "refund.result.confirm"

    data class Request(
        val channelId: String,
        val notificationId: String,
        val refundId: String,
        val refundAttemptId: String,
        val channelRefundId: String,
        val amount: BigDecimal,
        val currency: String,
        val result: String,
        val occurredAt: Instant,
        val verificationMaterial: String
    ) : EndpointRequest<Response>

    data class Response(
        val refundStatus: String,
        val attemptStatus: String?,
        val notificationReceiveCount: Int,
        val disposition: String,
        val duplicate: Boolean,
        val accepted: Boolean,
        val rejected: Boolean,
        val conflicting: Boolean,
        val reservationReleasedNow: Boolean,
        val reservationConvertedToSuccessNow: Boolean,
        val reviewRequiredNow: Boolean,
        val rejectionSummary: String?,
        val conflictSummary: String?
    )

}
