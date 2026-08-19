package com.only4.cap4k.reference.payment.application.commands.refund.review

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.markReviewRequired
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ReviewPendingRefunds",
    packageName = "refund.review",
    description = "Mark accepted refunds without final results as review required after the configured threshold",
    aggregates = ["Refund"],
    family = "command",
)
object ReviewPendingRefundsCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val now = LocalDateTime.ofInstant(command.now, ZoneOffset.UTC)
            val reviewedCount = Mediator.repositories.find(
                SRefund.predicate { schema -> schema.reservationActive eq true }
            ).count { it.markReviewRequired(now) }
            return Response(reviewedCount)
        }
    }

    data class Request(val now: Instant) : Command<Response>

    data class Response(val reviewedCount: Int)
}
