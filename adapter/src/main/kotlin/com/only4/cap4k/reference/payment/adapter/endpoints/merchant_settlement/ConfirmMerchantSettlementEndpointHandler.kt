package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toContractMoney
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.confirm.ConfirmMerchantSettlementCmd
import com.only4.cap4k.reference.payment.application.queries.merchant_settlement.read.GetMerchantSettlementQry
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read.MerchantSettlementContractStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ConfirmMerchantSettlementEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class ConfirmMerchantSettlementEndpointHandler(
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<ConfirmMerchantSettlementEndpoint.Request, ConfirmMerchantSettlementEndpoint.Response> {
    override fun handle(request: ConfirmMerchantSettlementEndpoint.Request): ConfirmMerchantSettlementEndpoint.Response {
        val actor = actorContextResolver.consume()
        val response = Mediator.commands.send(
            ConfirmMerchantSettlementCmd.Request(
                merchantSettlementId = MerchantSettlementId.parse(request.settlementId),
                operatorIdentity = actor.actorId,
                operatorRole = actor.role,
                idempotencyKey = request.idempotencyKey,
                reason = request.reason,
                evidence = request.evidence,
                confirmedAt = Instant.now(clock),
            )
        )
        val settlement = Mediator.queries.ask(GetMerchantSettlementQry.Request(response.merchantSettlementId))
        return ConfirmMerchantSettlementEndpoint.Response(
            response.merchantSettlementId.toString(),
            MerchantSettlementContractStatus.publicStatus(MerchantSettlementStatus.valueOf(response.status)),
            response.netAmount.toContractMoney(settlement.currency),
            response.actorId,
            response.confirmedAt,
            response.reason,
            response.evidence,
            response.receipt,
        )
    }
}
