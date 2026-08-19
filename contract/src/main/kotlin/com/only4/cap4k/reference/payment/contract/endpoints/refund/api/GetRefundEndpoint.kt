package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * GET /api/refunds/{refundId}
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetRefundEndpoint",
    packageName = "refund.api",
    description = "GET /api/refunds/{refundId}",
    aggregates = [],
    operationName = "refund.get",
    family = "endpoint"
)
object GetRefundEndpoint {
    const val OPERATION_NAME: String = "refund.get"

    data class Request(
        val refundId: String
    ) : EndpointRequest<Response>

    data class Response(
        val refundId: String,
        val paymentId: String,
        val merchantId: String,
        val merchantRefundNumber: String,
        val amount: BigDecimal,
        val currency: String,
        val paymentMethod: String,
        val status: String,
        val requestedAt: Instant,
        val refundDeadlineAt: Instant,
        val channelAcceptedAt: Instant?,
        val finalizedAt: Instant?,
        val reviewRequiredAt: Instant?,
        val channelId: String,
        val channelConfigurationId: String,
        val channelConfigurationSnapshot: String,
        val requestIdentity: String,
        val channelRefundId: String?,
        val reservationActive: Boolean,
        val reservationReleased: Boolean,
        val reservationConvertedToSuccess: Boolean,
        val successFactFormed: Boolean,
        val notificationReceiveCount: Int,
        val rejectedNotificationCount: Int,
        val conflictingNotificationCount: Int,
        val lastNotificationIdentity: String?,
        val lastNotificationReceivedAt: Instant?,
        val lastRejectionSummary: String?,
        val lastConflictSummary: String?,
        val settlementBlocked: Boolean,
        val attempts: List<RefundAttemptSummary>
    ) {
        data class RefundAttemptSummary(
            val refundAttemptId: String,
            val channelId: String,
            val status: String,
            val requestIdentity: String,
            val initiatedAt: Instant,
            val acceptedAt: Instant?,
            val reviewAfterAt: Instant,
            val channelRefundId: String?,
            val finalResult: String?,
            val resultOccurredAt: Instant?,
            val notificationReceiveCount: Int,
            val verifiedNotificationCount: Int,
            val rejectedNotificationCount: Int,
            val conflictingNotificationCount: Int,
            val verdictSummary: String?,
            val rejectionSummary: String?,
            val conflictSummary: String?,
            val notificationReceipts: List<RefundNotificationReceiptSummary>
        )
        data class RefundNotificationReceiptSummary(
            val notificationIdentity: String,
            val channelId: String,
            val channelRefundId: String,
            val amount: BigDecimal,
            val currency: String,
            val result: String,
            val occurredAt: Instant,
            val firstReceivedAt: Instant,
            val lastReceivedAt: Instant,
            val receiveCount: Int,
            val verified: Boolean,
            val accepted: Boolean,
            val decision: String,
            val verdictSummary: String?,
            val rejectionSummary: String?,
            val conflictSummary: String?
        )
    }

}
