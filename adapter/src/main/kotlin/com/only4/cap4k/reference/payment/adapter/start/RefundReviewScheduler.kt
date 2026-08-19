package com.only4.cap4k.reference.payment.adapter.start
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.refund.review.ReviewPendingRefundsCmd
import java.time.Clock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
@Component
class RefundReviewScheduler(private val clock:Clock) {
 @Scheduled(fixedDelayString="\${payment.refund.review-delay-ms:60000}")
 fun review(){ Mediator.commands.send(ReviewPendingRefundsCmd.Request(clock.instant())) }
}
