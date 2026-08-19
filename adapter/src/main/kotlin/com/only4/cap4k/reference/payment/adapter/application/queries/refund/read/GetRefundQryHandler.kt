package com.only4.cap4k.reference.payment.adapter.application.queries.refund.read

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.application.queries.refund.read.GetRefundQry
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "GetRefund",
    packageName = "refund.read",
    description = "Read a persisted refund with attempts and notification adjudication evidence",
    aggregates = ["Refund"],
    family = "query-handler",
)
class GetRefundQryHandler : QueryHandler<GetRefundQry.Request, GetRefundQry.Response> {

    override fun handle(query: GetRefundQry.Request): GetRefundQry.Response {
        val refund = Mediator.repositories.findOne(
            SRefund.predicateById(RefundId.parse(query.refundId))
        ) ?: throw RefundNotFoundException(query.refundId)
        return GetRefundQry.Response(
            refundId = refund.id.toString(),
            paymentId = refund.paymentId.toString(),
            merchantId = refund.merchantId,
            merchantRefundNumber = refund.merchantRefundNumber,
            amount = refund.amount,
            currency = refund.currency,
            paymentMethod = refund.paymentMethod,
            status = refund.status.name,
            requestedAt = refund.requestedAt.toInstant(ZoneOffset.UTC),
            refundDeadlineAt = refund.refundDeadlineAt.toInstant(ZoneOffset.UTC),
            channelAcceptedAt = refund.channelAcceptedAt?.toInstant(ZoneOffset.UTC),
            finalizedAt = refund.finalizedAt?.toInstant(ZoneOffset.UTC),
            reviewRequiredAt = refund.reviewRequiredAt?.toInstant(ZoneOffset.UTC),
            channelId = refund.channelId,
            channelConfigurationId = refund.channelConfigurationId,
            channelConfigurationSnapshot = refund.channelConfigurationSnapshot,
            requestIdentity = refund.requestIdentity,
            channelRefundId = refund.channelRefundId,
            reservationActive = refund.reservationActive,
            reservationReleased = refund.reservationReleased,
            reservationConvertedToSuccess = refund.reservationConvertedToSuccess,
            successFactFormed = refund.successFactFormed,
            notificationReceiveCount = refund.notificationReceiveCount,
            rejectedNotificationCount = refund.rejectedNotificationCount,
            conflictingNotificationCount = refund.conflictingNotificationCount,
            lastNotificationIdentity = refund.lastNotificationIdentity,
            lastNotificationReceivedAt = refund.lastNotificationReceivedAt?.toInstant(ZoneOffset.UTC),
            lastRejectionSummary = refund.lastRejectionSummary,
            lastConflictSummary = refund.lastConflictSummary,
            settlementBlocked = refund.settlementBlocked,
            attempts = refund.attempts.map { attempt ->
                GetRefundQry.Response.RefundAttemptSummary(
                    refundAttemptId = attempt.id.toString(),
                    channelId = attempt.channelId,
                    status = attempt.status.name,
                    requestIdentity = attempt.requestIdentity,
                    initiatedAt = attempt.initiatedAt.toInstant(ZoneOffset.UTC),
                    acceptedAt = attempt.acceptedAt?.toInstant(ZoneOffset.UTC),
                    reviewAfterAt = attempt.reviewAfterAt.toInstant(ZoneOffset.UTC),
                    channelRefundId = attempt.channelRefundId,
                    finalResult = attempt.finalResult?.name,
                    resultOccurredAt = attempt.resultOccurredAt?.toInstant(ZoneOffset.UTC),
                    notificationReceiveCount = attempt.notificationReceiveCount,
                    verifiedNotificationCount = attempt.verifiedNotificationCount,
                    rejectedNotificationCount = attempt.rejectedNotificationCount,
                    conflictingNotificationCount = attempt.conflictingNotificationCount,
                    verdictSummary = attempt.verdictSummary,
                    rejectionSummary = attempt.rejectionSummary,
                    conflictSummary = attempt.conflictSummary,
                    notificationReceipts = attempt.refundNotificationReceipts.map { receipt ->
                        GetRefundQry.Response.RefundNotificationReceiptSummary(
                            notificationIdentity = receipt.notificationIdentity,
                            channelId = receipt.channelId,
                            channelRefundId = receipt.channelRefundId,
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
