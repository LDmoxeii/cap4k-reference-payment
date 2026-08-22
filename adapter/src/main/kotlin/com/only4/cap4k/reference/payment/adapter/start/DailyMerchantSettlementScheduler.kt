package com.only4.cap4k.reference.payment.adapter.start

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.run.RunDailyMerchantSettlementCmd
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DailyMerchantSettlementScheduler(
    private val clock: Clock,
    @Value("\${payment.settlement.merchant-id:M-001}") private val merchantId: String,
    @Value("\${payment.settlement.channel-id:C-001}") private val channelId: String,
    @Value("\${payment.settlement.currency:CNY}") private val currency: String,
) {
    @Scheduled(cron = "\${payment.settlement.cron:0 15 0 * * *}", zone = "Asia/Shanghai")
    fun settlePreviousBusinessDay() {
        Mediator.commands.send(
            RunDailyMerchantSettlementCmd.Request(
                merchantId = merchantId,
                channelId = channelId,
                currency = currency,
                triggeredAt = clock.instant(),
            )
        )
    }
}
