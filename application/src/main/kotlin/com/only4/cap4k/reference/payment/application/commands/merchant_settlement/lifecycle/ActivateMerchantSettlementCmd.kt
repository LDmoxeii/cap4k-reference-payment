package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.lifecycle

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.activateEffectiveOwnership
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ActivateMerchantSettlement",
    packageName = "merchant_settlement.lifecycle",
    description = "Activate the effective scope and source-consumption ownership of a replacement settlement",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object ActivateMerchantSettlementCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {

        override fun handle(command: Request): Response {
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(MerchantSettlementId.parse(command.settlementId))
            ) ?: throw MerchantSettlementNotFoundException(command.settlementId)
            settlement.activateEffectiveOwnership()
            return Response(status = settlement.status.name)
        }
    }

    data class Request(
        val settlementId: String
    ) : Command<Response>

    data class Response(
        val status: String
    )

}
