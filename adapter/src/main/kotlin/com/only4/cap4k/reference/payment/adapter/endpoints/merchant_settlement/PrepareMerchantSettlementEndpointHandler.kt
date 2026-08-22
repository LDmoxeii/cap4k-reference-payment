package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.prepare.PrepareMerchantSettlementCmd
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.PrepareMerchantSettlementEndpoint
import org.springframework.stereotype.Component

@Component
class PrepareMerchantSettlementEndpointHandler : EndpointHandler<PrepareMerchantSettlementEndpoint.Request, PrepareMerchantSettlementEndpoint.Response> {
    override fun handle(request: PrepareMerchantSettlementEndpoint.Request): PrepareMerchantSettlementEndpoint.Response {
        val outcome = Mediator.commands.send(
            PrepareMerchantSettlementCmd.Request(
                merchantId = request.merchantId,
                channelId = request.channelId,
                currency = request.currency,
                settlementDate = request.settlementDate,
                requestedBy = request.requestedBy,
                requestedAt = request.requestedAt,
                predecessorSettlementId = null,
            )
        ).outcome
        return PrepareMerchantSettlementEndpoint.Response(
            settlementId = outcome.settlementId,
            status = outcome.status?.name,
            created = outcome.created,
            idempotentReplay = outcome.idempotentReplay,
            noOp = outcome.noOp,
            eligibleCount = outcome.eligibleCount,
            excludedCount = outcome.excludedCount,
            blockerSummary = outcome.blockerSummary,
            paymentGrossAmount = outcome.paymentGrossAmount,
            refundGrossAmount = outcome.refundGrossAmount,
            feeTotalAmount = outcome.feeTotalAmount,
            adjustmentTotalAmount = outcome.adjustmentTotalAmount,
            netAmount = outcome.netAmount,
        )
    }
}
