package com.only4.cap4k.reference.payment.adapter.start

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.payment.expiry.ExpirePaymentsCmd
import java.time.Clock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentExpiryScheduler(private val clock: Clock) {
    @Scheduled(fixedDelayString = "\${payment.expiry.scan-delay-ms:60000}")
    fun expirePayments() {
        Mediator.commands.send(ExpirePaymentsCmd.Request(clock.instant()))
    }
}
