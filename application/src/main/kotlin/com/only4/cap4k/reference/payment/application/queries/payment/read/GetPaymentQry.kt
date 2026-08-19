package com.only4.cap4k.reference.payment.application.queries.payment.read

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.Query
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "query",
    name = "GetPayment",
    packageName = "payment.read",
    description = "Read a persisted payment and all payment-attempt adjudication summaries",
    aggregates = ["Payment"],
    family = "query"
)
object GetPaymentQry {

    data class Request(
        val paymentId: String
    ) : Query<Response>

    data class Response(
        val paymentId: String,
        val merchantId: String,
        val merchantOrderNumber: String,
        val amount: BigDecimal,
        val currency: String,
        val paymentMethod: String,
        val status: String,
        val createdAt: Instant,
        val expiresAt: Instant,
        val succeededAt: Instant?,
        val channelTransactionId: String?,
        val reservedRefundAmount: BigDecimal,
        val successfulRefundAmount: BigDecimal,
        val refundableAmount: BigDecimal,
        val attemptCount: Int,
        val notificationReceiveCount: Int,
        val rejectedNotificationCount: Int,
        val conflictingNotificationCount: Int,
        val lastNotificationIdentity: String?,
        val lastNotificationReceivedAt: Instant?,
        val lastRejectionSummary: String?,
        val lastConflictSummary: String?,
        val successFactFormed: Boolean,
        val merchantSuccessNotificationIntentCount: Int,
        val settlementBlocked: Boolean,
        val attempts: List<PaymentAttemptSummary>
    ) {
        data class PaymentAttemptSummary(
            val paymentAttemptId: String,
            val channelId: String,
            val status: String,
            val requestIdentity: String,
            val initiatedAt: Instant,
            val channelTransactionId: String?,
            val finalResult: String?,
            val resultOccurredAt: Instant?,
            val notificationReceiveCount: Int,
            val notificationFirstReceivedAt: Instant?,
            val notificationLastReceivedAt: Instant?,
            val verifiedNotificationCount: Int,
            val rejectedNotificationCount: Int,
            val conflictingNotificationCount: Int,
            val verdictSummary: String?,
            val rejectionSummary: String?,
            val conflictSummary: String?,
            val notificationReceipts: List<NotificationReceiptSummary>,
        )

        data class NotificationReceiptSummary(
            val notificationIdentity: String,
            val channelId: String,
            val channelTransactionId: String,
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
            val conflictSummary: String?,
        )
    }
}
