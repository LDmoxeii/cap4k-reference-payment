package com.only4.cap4k.reference.payment.adapter.start

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RunDailyReconciliationCmd
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DailyReconciliationScheduler(
    private val clock: Clock,
    @Value("\${payment.reconciliation.channel-id:C-001}") private val channelId: String,
    @Value("\${payment.reconciliation.currency:CNY}") private val currency: String,
) {
    @Scheduled(cron = "\${payment.reconciliation.cron:0 5 0 * * *}", zone = "Asia/Shanghai")
    fun reconcilePreviousBusinessDay() {
        Mediator.commands.send(
            RunDailyReconciliationCmd.Request(
                channelId = channelId,
                currency = currency,
                triggeredAt = clock.instant(),
            )
        )
    }
}
