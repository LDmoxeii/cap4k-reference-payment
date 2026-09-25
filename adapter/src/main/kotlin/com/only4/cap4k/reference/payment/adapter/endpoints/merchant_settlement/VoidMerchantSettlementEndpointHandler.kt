package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.voiding.VoidMerchantSettlementCmd
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read.MerchantSettlementContractStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.VoidMerchantSettlementEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class VoidMerchantSettlementEndpointHandler(
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<VoidMerchantSettlementEndpoint.Request, VoidMerchantSettlementEndpoint.Response> {
    override fun handle(request: VoidMerchantSettlementEndpoint.Request): VoidMerchantSettlementEndpoint.Response {
        val actor = actorContextResolver.consume()
        val response = Mediator.commands.send(
            VoidMerchantSettlementCmd.Request(
                merchantSettlementId = MerchantSettlementId.parse(request.settlementId),
                operatorIdentity = actor.actorId,
                operatorRole = actor.role,
                reason = request.reason,
                evidence = request.evidence,
                idempotencyKey = request.idempotencyKey,
                voidedAt = Instant.now(clock),
                createReplacement = request.createReplacement,
            )
        )
        return VoidMerchantSettlementEndpoint.Response(
            response.merchantSettlementId.toString(),
            MerchantSettlementContractStatus.publicStatus(MerchantSettlementStatus.valueOf(response.status)),
            response.replacementSettlementId?.toString(),
            response.actorId,
            response.voidedAt,
            response.reason,
            response.evidence,
            response.receipt,
        )
    }
}
