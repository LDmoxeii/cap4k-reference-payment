package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.markUnknownReviewRequired
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.application.manual_review.openSettlementUnknownThresholdReview
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
    class Handler(
        private val manualReviewSupport: ManualReviewSupport,
    ) : CommandHandler<Request, Response> {
        /** 普通 scheduler 仅把超过冻结阈值的 UNKNOWN attempt 标记为需人工复核，绝不创建新的资金划拨尝试。 */
        override fun handle(command: Request): Response {
            val reviewedAt = LocalDateTime.ofInstant(command.reviewedAt, ZoneOffset.UTC)
            val settlements = Mediator.repositories.find(
                SMerchantSettlement.predicate { schema -> schema.status eq MerchantSettlementStatus.RESULT_UNKNOWN }
            )
            val changed = settlements.filter { it.markUnknownReviewRequired(reviewedAt) }
            changed.forEach { settlement ->
                settlement.settlementExecutionAttempts
                    .filter { it.status == SettlementExecutionAttemptStatus.REVIEW_REQUIRED }
                    .forEach { manualReviewSupport.openSettlementUnknownThresholdReview(settlement, it) }
            }
            return Response(changed.size)
        }
    }

    data class Request(val reviewedAt: Instant) : Command<Response>
    data class Response(val reviewedCount: Int)
}
