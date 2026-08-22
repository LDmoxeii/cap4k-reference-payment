package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.result.ConfirmMerchantSettlementResultCmd
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ConfirmMerchantSettlementResultEndpoint
import org.springframework.stereotype.Component

@Component
class ConfirmMerchantSettlementResultEndpointHandler : EndpointHandler<ConfirmMerchantSettlementResultEndpoint.Request, ConfirmMerchantSettlementResultEndpoint.Response> {
    override fun handle(request: ConfirmMerchantSettlementResultEndpoint.Request): ConfirmMerchantSettlementResultEndpoint.Response {
        val outcome = Mediator.commands.send(
            ConfirmMerchantSettlementResultCmd.Request(
                channelId = request.channelId,
                notificationId = request.notificationId,
                settlementId = request.settlementId,
                executionAttemptId = request.executionAttemptId,
                executionGroupIdentity = request.executionGroupIdentity,
                requestIdentity = request.requestIdentity,
                externalSettlementIdentity = request.externalSettlementIdentity,
                amount = request.amount,
                currency = request.currency,
                result = request.result,
                resultCode = request.resultCode,
                occurredAt = request.occurredAt,
                receivedAt = request.receivedAt,
                verificationMaterial = request.verificationMaterial,
            )
        ).outcome
        return ConfirmMerchantSettlementResultEndpoint.Response(
            settlementStatus = outcome.settlementStatus.name,
            attemptStatus = outcome.attemptStatus?.name,
            notificationReceiveCount = outcome.notificationReceiveCount,
            disposition = outcome.disposition.name,
            rejectionSummary = outcome.rejectionSummary,
            conflictSummary = outcome.conflictSummary,
            reviewSummary = outcome.reviewSummary,
            settledFactFormedNow = outcome.settledFactFormedNow,
        )
    }
}
