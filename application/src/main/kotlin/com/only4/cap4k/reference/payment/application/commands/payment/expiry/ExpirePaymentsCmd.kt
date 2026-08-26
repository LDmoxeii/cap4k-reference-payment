package com.only4.cap4k.reference.payment.application.commands.payment.expiry

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.expire
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(tag = "command", name = "ExpirePayments", packageName = "payment.expiry", description = "Idempotently close expired payments without pending attempts or move pending attempts into result review", aggregates = ["Payment"], family = "command")
object ExpirePaymentsCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        /** ordinary scheduler 只扫描候选并逐笔重新装载聚合；最终裁决由 Payment.expire 保证幂等和 callback 竞争收敛。 */
        override fun handle(command: Request): Response {
            val now = LocalDateTime.ofInstant(command.now, ZoneOffset.UTC)
            val outcomes = Mediator.repositories.find(
                SPayment.predicate { schema -> schema.expiresAt le now }
            ).map { it.expire(now) }
            return Response(
                inspectedCount = outcomes.size,
                closedCount = outcomes.count { it.closedNow },
                reviewOpenedCount = outcomes.count { it.reviewOpenedNow },
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
