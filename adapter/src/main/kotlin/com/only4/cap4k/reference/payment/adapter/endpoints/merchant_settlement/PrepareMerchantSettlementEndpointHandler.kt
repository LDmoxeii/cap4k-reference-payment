package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toContractMoney
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.prepare.PrepareMerchantSettlementCmd
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read.MerchantSettlementContractStatus
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.PrepareMerchantSettlementEndpoint
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class PrepareMerchantSettlementEndpointHandler(
    private val clock: Clock,
) : EndpointHandler<PrepareMerchantSettlementEndpoint.Request, PrepareMerchantSettlementEndpoint.Response> {
    override fun handle(request: PrepareMerchantSettlementEndpoint.Request): PrepareMerchantSettlementEndpoint.Response {
        val response = Mediator.commands.send(
            PrepareMerchantSettlementCmd.Request(
                merchantId = request.merchantId,
                currency = request.currency,
                periodStart = request.settlementPeriod.start,
                periodEnd = request.settlementPeriod.end,
                businessTimezone = request.settlementPeriod.timezone,
                requestedBy = REFERENCE_SYSTEM_ACTOR_ID,
                idempotencyKey = request.idempotencyKey,
                requestedAt = Instant.now(clock),
                predecessorSettlementId = null,
            )
        )
        val outcome = response.outcome
        return PrepareMerchantSettlementEndpoint.Response(
            settlementId = outcome.merchantSettlementId?.toString(),
            status = outcome.status?.let(MerchantSettlementContractStatus::publicStatus),
            created = outcome.created,
            idempotentReplay = outcome.idempotentReplay,
            noOp = outcome.noOp,
            eligibleCount = outcome.eligibleCount,
            excludedCount = outcome.excludedCount,
            blockerSummary = outcome.blockerSummary,
            grossMoney = outcome.paymentGrossAmount.toContractMoney(request.currency),
            refundMoney = outcome.refundGrossAmount.toContractMoney(request.currency),
            feeMoney = outcome.feeTotalAmount.toContractMoney(request.currency),
            adjustmentMoney = outcome.adjustmentTotalAmount.toContractMoney(request.currency),
            netMoney = outcome.netAmount.toContractMoney(request.currency),
            receipt = response.receipt,
        )
    }

    private companion object {
        const val REFERENCE_SYSTEM_ACTOR_ID = "reference-system"
    }
}
