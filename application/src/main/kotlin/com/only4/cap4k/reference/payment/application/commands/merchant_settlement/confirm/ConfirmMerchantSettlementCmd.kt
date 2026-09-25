package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.confirm

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.confirmComposition
import java.math.BigDecimal
import java.time.Instant
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ConfirmMerchantSettlement",
    packageName = "merchant_settlement.confirm",
    description = "Authorize and freeze a prepared merchant settlement composition",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object ConfirmMerchantSettlementCmd {
    @Service
    class Handler(
        private val operationSupport: OperationSupport,
        private val clock: Clock,
    ) : CommandHandler<Request, Response> {
        /** 授权确认只调用聚合冻结既有 composition；确认后任何候选、手续费或对账变化都不能原位改写该结算单。 */
        override fun handle(command: Request): Response {
            val key = command.idempotencyKey.trim()
            val reason = command.reason.trim()
            val evidence = command.evidence.trim()
            require(key.isNotBlank()) { "幂等键不能为空" }
            require(reason.isNotBlank()) { "确认原因不能为空" }
            require(evidence.isNotBlank()) { "确认证据不能为空" }
            require(command.operatorIdentity.isNotBlank() && command.operatorRole.trim().uppercase() == "SETTLEMENT_OPERATOR") {
                "当前操作员角色无权处理商户结算"
            }
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(command.merchantSettlementId)
            ) ?: throw MerchantSettlementNotFoundException(command.merchantSettlementId)
            val hash = operationSupport.canonicalHash(
                settlement.id.toString(), reason, evidence, command.operatorIdentity.trim(),
            )
            operationSupport.replayOrNull(settlement.merchantId, COMMAND_TYPE, key, hash)?.let { operation ->
                check(operation.resourceId == settlement.id.toString()) { "operation resource does not match settlement" }
                return Response(
                    settlement.id, settlement.status.name, settlement.netAmount,
                    operationSupport.receipt(operation, replay = true),
                    requireNotNull(settlement.confirmedAt).toInstant(ZoneOffset.UTC),
                    requireNotNull(settlement.confirmedReason), requireNotNull(settlement.confirmedEvidence),
                    requireNotNull(settlement.confirmedBy),
                )
            }
            settlement.confirmComposition(
                operatorIdentity = command.operatorIdentity,
                operatorRole = command.operatorRole,
                confirmedAt = LocalDateTime.now(clock),
                reason = reason,
                evidence = evidence,
            )
            val receipt = operationSupport.accept(
                settlement.merchantId, COMMAND_TYPE, key, hash,
                "MerchantSettlement", settlement.id.toString(), "/api/merchant-settlements/${settlement.id}",
            )
            return Response(
                settlement.id, settlement.status.name, settlement.netAmount, receipt,
                requireNotNull(settlement.confirmedAt).toInstant(ZoneOffset.UTC),
                reason, evidence, requireNotNull(settlement.confirmedBy),
            )
        }
    }

    data class Request(
        /**
         * 结算标识
         */
        val merchantSettlementId: MerchantSettlementId,
        /**
         * 操作员身份
         */
        val operatorIdentity: String,
        /**
         * 操作员角色
         */
        val operatorRole: String,
        val idempotencyKey: String,
        val reason: String,
        val evidence: String,
        /**
         * 确认时间
         */
        val confirmedAt: Instant
    ) : Command<Response>

    data class Response(
        /**
         * 结算标识
         */
        val merchantSettlementId: MerchantSettlementId,
        /**
         * 状态
         */
        val status: String,
        /**
         * 净金额
         */
        val netAmount: BigDecimal,
        val receipt: OperationReceipt,
        val confirmedAt: Instant,
        val reason: String,
        val evidence: String,
        val actorId: String,
    )
    private const val COMMAND_TYPE = "ConfirmMerchantSettlement"
}
