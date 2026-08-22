package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementExecutionAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.adjudicateUnknownResult
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementResultRecordingOutcome
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "AdjudicateMerchantSettlementResult",
    packageName = "merchant_settlement.review",
    description = "Append an authorized final conclusion to an unknown merchant settlement result without deleting receipt history",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object AdjudicateMerchantSettlementResultCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(MerchantSettlementId.parse(command.settlementId))
            ) ?: throw MerchantSettlementNotFoundException(command.settlementId)
            return Response(
                settlement.adjudicateUnknownResult(
                    attemptId = SettlementExecutionAttemptId.parse(command.executionAttemptId),
                    operatorIdentity = command.operatorIdentity,
                    operatorRole = command.operatorRole,
                    finalResult = command.finalResult,
                    adjudicatedAt = LocalDateTime.ofInstant(command.adjudicatedAt, ZoneOffset.UTC),
                    evidence = command.evidence,
                )
            )
        }
    }

    data class Request(
        val settlementId: String,
        val executionAttemptId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val finalResult: String,
        val adjudicatedAt: Instant,
        val evidence: String,
    ) : Command<Response>

    data class Response(val outcome: SettlementResultRecordingOutcome)
}
