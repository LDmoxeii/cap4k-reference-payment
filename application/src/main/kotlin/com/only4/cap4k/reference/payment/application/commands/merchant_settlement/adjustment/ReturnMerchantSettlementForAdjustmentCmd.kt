package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.adjustment

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.prepare.PrepareMerchantSettlementCmd
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementConflictException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.linkReplacement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.returnForAdjustment
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ReturnMerchantSettlementForAdjustment",
    packageName = "merchant_settlement.adjustment",
    description = "Return an unconfirmed merchant settlement for adjustment and prepare a fresh effective replacement",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object ReturnMerchantSettlementForAdjustmentCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val previous = findSettlement(command.settlementId)
            replayResponse(previous)?.let { return it }

            val returnedAt = command.returnedAt
            previous.returnForAdjustment(
                operatorIdentity = command.operatorIdentity,
                operatorRole = command.operatorRole,
                reason = command.reason,
                returnedAt = returnedAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
            )
            val settlementDate = previous.periodStart
                .toInstant(ZoneOffset.UTC)
                .atZone(ZoneId.of(previous.businessTimezone))
                .toLocalDate()
            val prepared = Mediator.commands.send(
                PrepareMerchantSettlementCmd.Request(
                    merchantId = previous.merchantId,
                    channelId = previous.channelId,
                    currency = previous.currency,
                    settlementDate = settlementDate,
                    requestedBy = command.operatorIdentity,
                    requestedAt = returnedAt,
                    predecessorSettlementId = previous.id.toString(),
                )
            ).outcome
            val replacementId = prepared.settlementId
                ?: throw MerchantSettlementConflictException(
                    code = "MERCHANT_SETTLEMENT_REPREPARE_EMPTY",
                    message = "returning settlement ${previous.id} produced no eligible replacement",
                )
            require(replacementId != previous.id.toString()) { "replacement settlement must differ from the returned settlement" }
            previous.linkReplacement(MerchantSettlementId.parse(replacementId))
            return Response(
                previousSettlementId = previous.id.toString(),
                previousStatus = previous.status.name,
                replacementSettlementId = replacementId,
                replacementStatus = requireNotNull(prepared.status).name,
            )
        }

        private fun replayResponse(previous: MerchantSettlement): Response? {
            if (previous.status != MerchantSettlementStatus.VOIDED ||
                previous.voidReason?.startsWith(RETURN_REASON_PREFIX) != true ||
                previous.replacementSettlementId == null
            ) return null
            return response(previous, findSettlement(requireNotNull(previous.replacementSettlementId)))
        }

        private fun findSettlement(settlementId: String): MerchantSettlement =
            Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(MerchantSettlementId.parse(settlementId))
            ) ?: throw MerchantSettlementNotFoundException(settlementId)

        private fun response(previous: MerchantSettlement, replacement: MerchantSettlement) = Response(
            previousSettlementId = previous.id.toString(),
            previousStatus = previous.status.name,
            replacementSettlementId = replacement.id.toString(),
            replacementStatus = replacement.status.name,
        )
    }

    data class Request(
        val settlementId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val reason: String,
        val returnedAt: Instant
    ) : Command<Response>

    data class Response(
        val previousSettlementId: String,
        val previousStatus: String,
        val replacementSettlementId: String,
        val replacementStatus: String
    )

    private const val RETURN_REASON_PREFIX = "RETURN_FOR_ADJUSTMENT:"
}