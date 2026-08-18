package com.only4.cap4k.reference.payment.adapter.application.queries.payment.read

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.queries.payment.read.GetPaymentQry
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "GetPayment",
    packageName = "payment.read",
    description = "Read a persisted payment and all payment-attempt adjudication summaries",
    aggregates = ["Payment"],
    family = "query-handler"
)
class GetPaymentQryHandler : QueryHandler<GetPaymentQry.Request, GetPaymentQry.Response> {

    override fun handle(query: GetPaymentQry.Request): GetPaymentQry.Response {
        val payment = Mediator.repositories.findOne(
            SPayment.predicateById(PaymentId.parse(query.paymentId))
        ) ?: throw PaymentNotFoundException(query.paymentId)
        return GetPaymentQry.Response(
            paymentId = payment.id.toString(),
            merchantId = payment.merchantId,
            merchantOrderNumber = payment.merchantOrderNumber,
            amount = payment.amount,
            currency = payment.currency,
            paymentMethod = payment.paymentMethod,
            status = payment.status.name,
            createdAt = requireNotNull(payment.createdAt) { "payment ${payment.id} has no createdAt" }.toInstant(ZoneOffset.UTC),
            expiresAt = payment.expiresAt.toInstant(ZoneOffset.UTC),
            succeededAt = payment.succeededAt?.toInstant(ZoneOffset.UTC),
            channelTransactionId = payment.channelTransactionId,
            attemptCount = payment.attemptCount,
            notificationReceiveCount = payment.notificationReceiveCount,
            rejectedNotificationCount = payment.rejectedNotificationCount,
            conflictingNotificationCount = payment.conflictingNotificationCount,
            lastNotificationIdentity = payment.lastNotificationIdentity,
            lastNotificationReceivedAt = payment.lastNotificationReceivedAt?.toInstant(ZoneOffset.UTC),
            lastRejectionSummary = payment.lastRejectionSummary,
            lastConflictSummary = payment.lastConflictSummary,
            successFactFormed = payment.successFactFormed,
            merchantSuccessNotificationIntentCount = payment.merchantSuccessNotificationIntentCount,
            settlementBlocked = payment.settlementBlocked,
            attempts = payment.attempts.map { attempt ->
                GetPaymentQry.Response.PaymentAttemptSummary(
                    paymentAttemptId = attempt.id.toString(),
                    channelId = attempt.channelId,
                    status = attempt.status.name,
                    requestIdentity = attempt.requestIdentity,
                    initiatedAt = attempt.initiatedAt.toInstant(ZoneOffset.UTC),
                    channelTransactionId = attempt.channelTransactionId,
                    finalResult = attempt.finalResult?.name,
                    resultOccurredAt = attempt.resultOccurredAt?.toInstant(ZoneOffset.UTC),
                    notificationReceiveCount = attempt.notificationReceiveCount,
                    notificationFirstReceivedAt = attempt.notificationFirstReceivedAt?.toInstant(ZoneOffset.UTC),
                    notificationLastReceivedAt = attempt.notificationLastReceivedAt?.toInstant(ZoneOffset.UTC),
                    verifiedNotificationCount = attempt.verifiedNotificationCount,
                    rejectedNotificationCount = attempt.rejectedNotificationCount,
                    conflictingNotificationCount = attempt.conflictingNotificationCount,
                    verdictSummary = attempt.verdictSummary,
                    rejectionSummary = attempt.rejectionSummary,
                    conflictSummary = attempt.conflictSummary,
                    notificationReceipts = attempt.paymentNotificationReceipts.map { receipt ->
                        GetPaymentQry.Response.NotificationReceiptSummary(
                            notificationIdentity = receipt.notificationIdentity,
                            channelId = receipt.channelId,
                            channelTransactionId = receipt.channelTransactionId,
                            amount = receipt.amount,
                            currency = receipt.currency,
                            result = receipt.result,
                            occurredAt = receipt.occurredAt.toInstant(ZoneOffset.UTC),
                            firstReceivedAt = receipt.firstReceivedAt.toInstant(ZoneOffset.UTC),
                            lastReceivedAt = receipt.lastReceivedAt.toInstant(ZoneOffset.UTC),
                            receiveCount = receipt.receiveCount,
                            verified = receipt.verified,
                            accepted = receipt.accepted,
                            decision = receipt.decision.name,
                            verdictSummary = receipt.verdictSummary,
                            rejectionSummary = receipt.rejectionSummary,
                            conflictSummary = receipt.conflictSummary,
                        )
                    },
                )
            },
        )
    }
}
