package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.confirm

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.confirmComposition
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ConfirmMerchantSettlement",
    packageName = "merchant_settlement.confirm",
    description = "Authorize and freeze a prepared merchant settlement composition",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object ConfirmMerchantSettlementCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(MerchantSettlementId.parse(command.settlementId))
            ) ?: throw MerchantSettlementNotFoundException(command.settlementId)
            settlement.confirmComposition(
                operatorIdentity = command.operatorIdentity,
                operatorRole = command.operatorRole,
                confirmedAt = LocalDateTime.ofInstant(command.confirmedAt, ZoneOffset.UTC),
            )
            return Response(settlement.id.toString(), settlement.status.name, settlement.netAmount)
        }
    }

    data class Request(
        val settlementId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val confirmedAt: Instant
    ) : Command<Response>

    data class Response(val settlementId: String, val status: String, val netAmount: BigDecimal)
}
