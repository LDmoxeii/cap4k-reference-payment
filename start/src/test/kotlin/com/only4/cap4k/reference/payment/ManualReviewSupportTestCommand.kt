package com.only4.cap4k.reference.payment

import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import org.springframework.stereotype.Service

/** Test-only command: exercises the same CAP4K command/UoW scope the production hooks use. */
internal object ManualReviewSupportTestCommand {
    data class Request(
        val opening: ManualReviewSupport.Opening,
        val rollbackAfterOpen: Boolean = false,
    ) : Command<ManualReviewSupport.Opened>

    @Service
    class Handler(
        private val manualReviews: ManualReviewSupport,
    ) : CommandHandler<Request, ManualReviewSupport.Opened> {
        override fun handle(command: Request): ManualReviewSupport.Opened {
            val opened = manualReviews.open(command.opening)
            if (command.rollbackAfterOpen) error("force business command rollback")
            return opened
        }
    }
}
