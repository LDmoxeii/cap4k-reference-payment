package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.markUnknownReviewRequired
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ReviewUnknownMerchantSettlements",
    packageName = "merchant_settlement.review",
    description = "Move overdue unknown settlement attempts into manual review without creating a payment retry",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object ReviewUnknownMerchantSettlementsCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val reviewedAt = LocalDateTime.ofInstant(command.reviewedAt, ZoneOffset.UTC)
            val settlements = Mediator.repositories.find(
                SMerchantSettlement.predicate { schema -> schema.status eq MerchantSettlementStatus.RESULT_UNKNOWN }
            )
            return Response(settlements.count { it.markUnknownReviewRequired(reviewedAt) })
        }
    }

    data class Request(val reviewedAt: Instant) : Command<Response>
    data class Response(val reviewedCount: Int)
}
