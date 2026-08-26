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
                    message = "退回结算单 ${previous.id} 后没有可生成的有效替代单",
                )
            require(replacementId != previous.id.toString()) { "替代结算单必须与被退回结算单不同" }
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
        /**
         * 结算标识
         */
        val settlementId: String,
        /**
         * 操作员身份
         */
        val operatorIdentity: String,
        /**
         * 操作员角色
         */
        val operatorRole: String,
        /**
         * 原因
         */
        val reason: String,
        /**
         * 返回时间
         */
        val returnedAt: Instant
    ) : Command<Response>

    data class Response(
        /**
         * 前一结算标识
         */
        val previousSettlementId: String,
        /**
         * 前一状态
         */
        val previousStatus: String,
        /**
         * 替代结算标识
         */
        val replacementSettlementId: String,
        /**
         * 替代状态
         */
        val replacementStatus: String
    )

    private const val RETURN_REASON_PREFIX = "RETURN_FOR_ADJUSTMENT:"
}