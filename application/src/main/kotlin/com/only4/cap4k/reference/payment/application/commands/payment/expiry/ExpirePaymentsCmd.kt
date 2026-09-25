package com.only4.cap4k.reference.payment.application.commands.payment.expiry

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.or
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.application.manual_review.openPaymentReview
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.domain.aggregates.payment.expire
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(tag = "command", name = "ExpirePayments", packageName = "payment.expiry", description = "Idempotently close expired payments without pending attempts or move pending attempts into result review", aggregates = ["Payment"], family = "command")
object ExpirePaymentsCmd {
    @Service
    class Handler(
        private val manualReviewSupport: ManualReviewSupport,
        private val referencePolicy: ReferencePolicyService,
    ) : CommandHandler<Request, Response> {
        /** ordinary scheduler 只扫描候选并逐笔重新装载聚合；最终裁决由 Payment.expire 保证幂等和 callback 竞争收敛。 */
        override fun handle(command: Request): Response {
            val now = LocalDateTime.ofInstant(command.now, ZoneOffset.UTC)
            val unknownResultReviewAfter = referencePolicy.current().unknownResultReviewAfter
            val outcomes = Mediator.repositories.find(
                SPayment.predicate { schema ->
                    (schema.expiresAt le now) or (schema.status eq PaymentStatus.RESULT_UNKNOWN)
                }
            ).map { payment -> payment to payment.expire(now, unknownResultReviewAfter) }
            outcomes.forEach { (payment, outcome) ->
                if (outcome.reviewOpenedNow) {
                    outcome.reviewIdentity?.let { manualReviewSupport.openPaymentReview(payment, it) }
                }
            }
            return Response(
                inspectedCount = outcomes.size,
                closedCount = outcomes.count { it.second.closedNow },
                reviewOpenedCount = outcomes.count { it.second.reviewOpenedNow },
            )
        }
    }
    data class Request(
        /**
         * 当前时间
         */
        val now: Instant
    ) : Command<Response>
    data class Response(
        /**
         * 检查数量
         */
        val inspectedCount: Int,
        /**
         * 关闭数量
         */
        val closedCount: Int,
        /**
         * 已打开复核数量
         */
        val reviewOpenedCount: Int
    )
}
