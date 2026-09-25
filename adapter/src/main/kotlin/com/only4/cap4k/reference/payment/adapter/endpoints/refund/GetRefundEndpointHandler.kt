package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.contract.ReferenceContractStatusMapper
import com.only4.cap4k.reference.payment.adapter.endpoints.toContractMoney
import com.only4.cap4k.reference.payment.application.queries.refund.read.GetRefundQry
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.GetRefundEndpoint
import org.springframework.stereotype.Component

@Component
class GetRefundEndpointHandler : EndpointHandler<GetRefundEndpoint.Request, GetRefundEndpoint.Response> {
    override fun handle(request: GetRefundEndpoint.Request): GetRefundEndpoint.Response {
        val response = Mediator.queries.ask(GetRefundQry.Request(RefundId.parse(request.refundId)))
        return GetRefundEndpoint.Response(
            refundId = response.refundId.toString(),
            paymentId = response.paymentId.toString(),
            merchantId = response.merchantId,
            merchantRefundNumber = response.merchantRefundNumber,
            idempotencyKey = response.idempotencyKey,
            money = response.amount.toContractMoney(response.currency),
            reason = response.reason,
            paymentMethod = response.paymentMethod,
            status = ReferenceContractStatusMapper.refundStatus(response.status),
            finality = ReferenceContractStatusMapper.refundFinality(response.status, response.settlementBlocked),
            requestedAt = response.requestedAt,
            refundDeadlineAt = response.refundDeadlineAt,
            channelAcceptedAt = response.channelAcceptedAt,
            finalizedAt = response.finalizedAt,
            reviewRequiredAt = response.reviewRequiredAt,
            channelId = response.channelId,
            channelConfigurationId = response.channelConfigurationId,
            channelConfigurationSnapshot = response.channelConfigurationSnapshot,
            requestIdentity = response.requestIdentity,
            channelRefundId = response.channelRefundId,
            reservationActive = response.reservationActive,
            reservationReleased = response.reservationReleased,
            reservationConvertedToSuccess = response.reservationConvertedToSuccess,
            successFactFormed = response.successFactFormed,
            notificationReceiveCount = response.notificationReceiveCount,
            rejectedNotificationCount = response.rejectedNotificationCount,
            conflictingNotificationCount = response.conflictingNotificationCount,
            lastNotificationIdentity = response.lastNotificationIdentity,
            lastNotificationReceivedAt = response.lastNotificationReceivedAt,
            lastRejectionSummary = response.lastRejectionSummary,
            lastConflictSummary = response.lastConflictSummary,
            settlementBlocked = response.settlementBlocked,
            attempts = response.attempts.map { attempt ->
                GetRefundEndpoint.Response.RefundAttemptSummary(
                    refundAttemptId = attempt.refundAttemptId,
                    channelId = attempt.channelId,
                    status = ReferenceContractStatusMapper.refundAttemptStatus(attempt.status),
                    requestIdentity = attempt.requestIdentity,
                    initiatedAt = attempt.initiatedAt,
                    acceptedAt = attempt.acceptedAt,
                    reviewAfterAt = attempt.reviewAfterAt,
                    channelRefundId = attempt.channelRefundId,
                    finalResult = attempt.finalResult,
                    resultOccurredAt = attempt.resultOccurredAt,
                    notificationReceiveCount = attempt.notificationReceiveCount,
                    verifiedNotificationCount = attempt.verifiedNotificationCount,
                    rejectedNotificationCount = attempt.rejectedNotificationCount,
                    conflictingNotificationCount = attempt.conflictingNotificationCount,
                    verdictSummary = attempt.verdictSummary,
                    rejectionSummary = attempt.rejectionSummary,
                    conflictSummary = attempt.conflictSummary,
                    notificationReceipts = attempt.notificationReceipts.map { receipt ->
                        GetRefundEndpoint.Response.RefundNotificationReceiptSummary(
                            notificationIdentity = receipt.notificationIdentity,
                            channelId = receipt.channelId,
                            channelRefundId = receipt.channelRefundId,
                            money = receipt.amount.toContractMoney(receipt.currency),
                            result = receipt.result,
                            occurredAt = receipt.occurredAt,
                            firstReceivedAt = receipt.firstReceivedAt,
                            lastReceivedAt = receipt.lastReceivedAt,
                            receiveCount = receipt.receiveCount,
                            verified = receipt.verified,
                            accepted = receipt.accepted,
                            decision = ReferenceContractStatusMapper.refundDisposition(receipt.decision),
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
