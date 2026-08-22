package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.run

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.prepare.PrepareMerchantSettlementCmd
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementPreparationOutcome
import java.time.Instant
import java.time.ZoneId
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "RunDailyMerchantSettlement",
    packageName = "merchant_settlement.run",
    description = "Scheduled daily merchant settlement preparation for one merchant channel currency scope",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object RunDailyMerchantSettlementCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val settlementDate = command.triggeredAt.atZone(ZoneId.of(BUSINESS_TIMEZONE)).toLocalDate().minusDays(1)
            val prepared = Mediator.commands.send(
                PrepareMerchantSettlementCmd.Request(
                    merchantId = command.merchantId,
                    channelId = command.channelId,
                    currency = command.currency,
                    settlementDate = settlementDate,
                    requestedBy = "daily-settlement-scheduler",
                    requestedAt = command.triggeredAt,
                    predecessorSettlementId = null,
                )
            )
            return Response(prepared.outcome)
        }
    }

    data class Request(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val triggeredAt: Instant
    ) : Command<Response>

    data class Response(val outcome: SettlementPreparationOutcome)
    private const val BUSINESS_TIMEZONE = "Asia/Shanghai"
}
