package com.only4.cap4k.reference.payment.adapter.start

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review.ReviewUnknownMerchantSettlementsCmd
import java.time.Clock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class UnknownMerchantSettlementReviewScheduler(private val clock: Clock) {
    @Scheduled(fixedDelayString = "\${payment.settlement.review-delay-ms:60000}")
    fun reviewUnknownSettlements() {
        Mediator.commands.send(ReviewUnknownMerchantSettlementsCmd.Request(clock.instant()))
    }
}
