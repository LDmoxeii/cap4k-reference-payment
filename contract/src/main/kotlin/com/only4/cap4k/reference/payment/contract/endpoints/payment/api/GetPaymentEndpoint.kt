package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/** GET /api/payments/{paymentId} */
@DesignBlockMetadata(tag = "endpoint", name = "GetPaymentEndpoint", packageName = "payment.api", description = "GET /api/payments/{paymentId}", aggregates = [], operationName = "payment.get", family = "endpoint")
object GetPaymentEndpoint {
    const val OPERATION_NAME = "payment.get"
    data class Request(val paymentId: String) : EndpointRequest<Response>
    data class Response(
        val paymentId: String, val merchantId: String, val merchantOrderNumber: String,
        val amount: BigDecimal, val currency: String, val paymentMethod: String, val status: String,
        val createdAt: Instant, val expiresAt: Instant, val succeededAt: Instant?, val closedAt: Instant?,
        val closeReason: String?, val channelTransactionId: String?, val reservedRefundAmount: BigDecimal,
        val successfulRefundAmount: BigDecimal, val refundableAmount: BigDecimal, val attemptCount: Int,
        val notificationReceiveCount: Int, val rejectedNotificationCount: Int,
        val conflictingNotificationCount: Int, val lastNotificationIdentity: String?,
        val lastNotificationReceivedAt: Instant?, val lastRejectionSummary: String?,
        val lastConflictSummary: String?, val successFactFormed: Boolean,
        val merchantOrderSuccessIdentity: String?, val merchantSuccessNotificationIntentCount: Int,
        val merchantSuccessNotificationIntentIdentity: String?, val merchantSuccessNotificationIntentState: String?,
        val reviewCount: Int, val blockingReviewCount: Int, val settlementEligible: Boolean,
        val settlementBlocked: Boolean, val attempts: List<PaymentAttemptSummary>,
        val reviews: List<PaymentReviewSummary>,
    ) {
        data class PaymentAttemptSummary(
            val paymentAttemptId: String, val channelId: String, val status: String,
            val requestIdentity: String, val initiatedAt: Instant, val channelTransactionId: String?,
            val finalResult: String?, val resultOccurredAt: Instant?, val notificationReceiveCount: Int,
            val notificationFirstReceivedAt: Instant?, val notificationLastReceivedAt: Instant?,
            val verifiedNotificationCount: Int, val rejectedNotificationCount: Int,
            val conflictingNotificationCount: Int, val verdictSummary: String?, val rejectionSummary: String?,
            val conflictSummary: String?, val notificationReceipts: List<NotificationReceiptSummary>,
        )
        data class NotificationReceiptSummary(
            val notificationIdentity: String, val payloadIdentity: String, val channelId: String,
            val channelTransactionId: String, val amount: BigDecimal, val currency: String, val result: String,
            val occurredAt: Instant, val firstReceivedAt: Instant, val lastReceivedAt: Instant,
            val receiveCount: Int, val verified: Boolean, val accepted: Boolean, val decision: String,
            val verdictSummary: String?, val rejectionSummary: String?, val conflictSummary: String?,
        )
        data class PaymentReviewSummary(
            val reviewId: String, val reviewIdentity: String, val type: String, val status: String,
            val openedAt: Instant, val summary: String, val settlementImpact: String, val resolvedAt: Instant?,
            val triggeringAttemptIdentities: String?, val triggeringReceiptIdentities: String?,
            val decisions: List<PaymentReviewDecisionSummary>,
        )
        data class PaymentReviewDecisionSummary(
            val decisionId: String, val decisionIdentity: String, val decision: String,
            val operatorIdentity: String, val operatorRole: String, val authorizationOutcome: Boolean,
            val reason: String, val evidence: String, val decidedAt: Instant, val eligibilityImpact: String,
            val remediationReference: String?,
        )
    }
}
