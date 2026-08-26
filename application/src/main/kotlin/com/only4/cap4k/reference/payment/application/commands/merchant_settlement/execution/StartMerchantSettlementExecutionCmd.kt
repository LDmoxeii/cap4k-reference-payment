package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.execution

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.transfer.StartSettlementTransfer
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementConflictException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.markExecutionAccepted
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.rejectExecutionStart
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.startExecutionAttempt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "StartMerchantSettlementExecution",
    packageName = "merchant_settlement.execution",
    description = "Submit a confirmed positive settlement or retry after an explicit failed attempt",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object StartMerchantSettlementExecutionCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        /**
         * 只有确认后的正净额结算单可以提交资金划拨；PROCESSING 重放复用 attempt，明确失败后才允许新 attempt。
         * RESULT_UNKNOWN 始终拒绝重付。provider 原始异常只写日志/内部诊断，对外保留稳定失败 code 与中文摘要。
         */
        override fun handle(command: Request): Response {
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(command.merchantSettlementId)
            ) ?: throw MerchantSettlementNotFoundException(command.merchantSettlementId)
            if (settlement.status in setOf(
                    MerchantSettlementStatus.RESULT_UNKNOWN,
                    MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED,
                )
            ) {
                throw MerchantSettlementConflictException(
                    code = "MERCHANT_SETTLEMENT_RESULT_UNRESOLVED",
                    message = "商户结算单 ${settlement.id} 的前次执行结果尚未解决，不能创建新的执行",
                )
            }
            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq settlement.merchantId) and
                        (schema.channelId eq settlement.channelId) and
                        (schema.currency eq settlement.currency) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE)
                }
            ) ?: error("结算单 ${settlement.id} 没有可用渠道配置")
            val requestedAt = LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC)
            val groupIdentity = settlement.executionGroupIdentity ?: "SETTLEMENT:${settlement.id}"
            val requestIdentity = "$groupIdentity:${settlement.settlementExecutionAttempts.size + 1}"
            val attempt = settlement.startExecutionAttempt(
                operatorIdentity = command.operatorIdentity,
                operatorRole = command.operatorRole,
                requestedAt = requestedAt,
                reviewAfterMinutes = configuration.settlementResultReviewAfterMinutes,
                executionGroupIdentity = groupIdentity,
                requestIdentity = requestIdentity,
            )
            if (attempt.requestIdentity != requestIdentity) {
                return Response(
                    merchantSettlementId = settlement.id,
                    attemptId = attempt.id.toString(),
                    executionGroupIdentity = attempt.executionGroupIdentity,
                    requestIdentity = attempt.requestIdentity,
                    status = settlement.status.name,
                    providerAccepted = attempt.acceptedAt != null,
                    diagnosticSummary = "复用了当前正在处理的结算执行尝试",
                )
            }
            val transfer = try {
                Mediator.capabilities.call(
                    StartSettlementTransfer.Request(
                        merchantSettlementId = settlement.id,
                        executionAttemptId = attempt.id.toString(),
                        merchantId = settlement.merchantId,
                        channelId = settlement.channelId,
                        executionGroupIdentity = groupIdentity,
                        requestIdentity = requestIdentity,
                        amount = settlement.netAmount,
                        currency = settlement.currency,
                    )
                )
            } catch (failure: RuntimeException) {
                val diagnostic = "结算资金划拨 provider 调用失败"
                settlement.rejectExecutionStart(attempt.id, "SETTLEMENT_GATEWAY_ERROR", diagnostic)
                return response(settlement, attempt.id.toString(), false, diagnostic)
            }
            if (!transfer.accepted || transfer.externalSettlementIdentity.isNullOrBlank()) {
                val diagnostic = listOfNotNull(
                    transfer.failureCode ?: "SETTLEMENT_GATEWAY_REJECTED",
                    transfer.diagnosticSummary,
                ).joinToString(": ")
                settlement.rejectExecutionStart(
                    attempt.id,
                    transfer.failureCode ?: "SETTLEMENT_GATEWAY_REJECTED",
                    transfer.diagnosticSummary,
                )
                return response(settlement, attempt.id.toString(), false, diagnostic)
            }
            settlement.markExecutionAccepted(attempt.id, transfer.externalSettlementIdentity, requestedAt)
            return response(settlement, attempt.id.toString(), true, transfer.diagnosticSummary)
        }

        private fun response(
            settlement: com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement,
            attemptId: String,
            accepted: Boolean,
            diagnostic: String?,
        ): Response = Response(
            merchantSettlementId = settlement.id,
            attemptId = attemptId,
            executionGroupIdentity = settlement.executionGroupIdentity,
            requestIdentity = settlement.settlementExecutionAttempts.lastOrNull()?.requestIdentity,
            status = settlement.status.name,
            providerAccepted = accepted,
            diagnosticSummary = diagnostic,
        )
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
        /**
         * 请求时间
         */
        val requestedAt: Instant
    ) : Command<Response>

    data class Response(
        /**
         * 结算标识
         */
        val merchantSettlementId: MerchantSettlementId,
        /**
         * 尝试标识
         */
        val attemptId: String?,
        /**
         * 执行组身份
         */
        val executionGroupIdentity: String?,
        /**
         * 请求身份
         */
        val requestIdentity: String?,
        /**
         * 状态
         */
        val status: String,
        /**
         * 渠道是否接受
         */
        val providerAccepted: Boolean,
        /**
         * 诊断摘要
         */
        val diagnosticSummary: String?
    )
}
