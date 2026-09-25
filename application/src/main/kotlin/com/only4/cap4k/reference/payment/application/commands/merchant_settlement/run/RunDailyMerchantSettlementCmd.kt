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
            val zone = ZoneId.of(BUSINESS_TIMEZONE)
            val settlementDate = command.triggeredAt.atZone(zone).toLocalDate().minusDays(1)
            val periodStart = settlementDate.atStartOfDay(zone).toInstant()
            val periodEnd = settlementDate.plusDays(1).atStartOfDay(zone).toInstant()
            val prepared = Mediator.commands.send(
                PrepareMerchantSettlementCmd.Request(
                    merchantId = command.merchantId,
                    currency = command.currency,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    businessTimezone = BUSINESS_TIMEZONE,
                    requestedBy = "daily-settlement-scheduler",
                    idempotencyKey = "daily:$periodStart:$periodEnd:${command.merchantId.trim()}:${command.currency.trim().uppercase()}",
                    requestedAt = command.triggeredAt,
                    predecessorSettlementId = null,
                )
            )
            return Response(prepared.outcome)
        }
    }

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 触发时间
         */
        val triggeredAt: Instant
    ) : Command<Response>

    data class Response(val outcome: SettlementPreparationOutcome)
    private const val BUSINESS_TIMEZONE = "Asia/Shanghai"
}
