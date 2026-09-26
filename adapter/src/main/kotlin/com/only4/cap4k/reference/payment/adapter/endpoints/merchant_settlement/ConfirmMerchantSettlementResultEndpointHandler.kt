package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toDomainAmount
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.result.ConfirmMerchantSettlementResultCmd
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read.MerchantSettlementContractStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ConfirmMerchantSettlementResultEndpoint
import org.springframework.stereotype.Component

@Component
class ConfirmMerchantSettlementResultEndpointHandler : EndpointHandler<ConfirmMerchantSettlementResultEndpoint.Request, ConfirmMerchantSettlementResultEndpoint.Response> {
    override fun handle(request: ConfirmMerchantSettlementResultEndpoint.Request): ConfirmMerchantSettlementResultEndpoint.Response {
        val commandResponse = Mediator.commands.send(
            ConfirmMerchantSettlementResultCmd.Request(
                channelId = request.channelId,
                notificationId = request.notificationId,
                merchantSettlementId = MerchantSettlementId.parse(request.settlementId),
                executionAttemptId = request.executionAttemptId,
                executionId = request.executionId,
                executionGroupIdentity = request.executionGroupIdentity,
                requestIdentity = request.requestIdentity,
                externalSettlementIdentity = request.externalSettlementIdentity,
                amount = request.money.toDomainAmount(),
                currency = request.money.currency,
                result = request.result,
                resultCode = request.resultCode,
                occurredAt = request.occurredAt,
                receivedAt = request.receivedAt,
                rawPayload = request.rawPayload,
            )
        )
        val outcome = commandResponse.outcome
        return ConfirmMerchantSettlementResultEndpoint.Response(
            executionId = commandResponse.executionId,
            settlementStatus = MerchantSettlementContractStatus.publicStatus(outcome.settlementStatus),
            attemptStatus = outcome.attemptStatus?.let(MerchantSettlementContractStatus::publicExecutionStatus),
            notificationReceiveCount = outcome.notificationReceiveCount,
            disposition = outcome.disposition.name,
            rejectionSummary = outcome.rejectionSummary,
            conflictSummary = outcome.conflictSummary,
            reviewSummary = outcome.reviewSummary,
            settledFactFormedNow = outcome.settledFactFormedNow,
            receipt = commandResponse.receipt,
        )
    }
}
