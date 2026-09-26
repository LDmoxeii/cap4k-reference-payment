package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.execution

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.transfer.StartSettlementTransfer
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementConflictException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain._share.meta.operation.SOperation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.markExecutionAccepted
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.markExecutionNoResult
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.recordExecutorObservation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.recordSettlementResult
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.rejectExecutionStart
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.startExecutionAttempt
import java.time.Instant
import java.time.Clock
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
    class Handler(
        private val operationSupport: OperationSupport,
        private val clock: Clock,
    ) : CommandHandler<Request, Response> {
        /**
         * 只有确认后的正净额结算单可以提交资金划拨；PROCESSING 重放复用 attempt，明确失败后才允许新 attempt。
         * RESULT_UNKNOWN 始终拒绝重付。provider 原始异常只写日志/内部诊断，对外保留稳定失败 code 与中文摘要。
         */
        override fun handle(command: Request): Response {
            val key = command.idempotencyKey.trim()
            val executionChannelId = command.executionChannelId.trim()
            val executionId = command.executionId.trim()
            val merchantId = command.merchantId.trim()
            require(key.isNotBlank()) { "幂等键不能为空" }
            require(executionChannelId.isNotBlank()) { "执行渠道不能为空" }
            require(executionId.isNotBlank()) { "executionId 不能为空" }
            require(merchantId.isNotBlank()) { "merchantId 不能为空" }
            require(command.operatorIdentity.isNotBlank() && command.operatorRole.trim().uppercase() == "SETTLEMENT_OPERATOR") {
                "当前操作员角色无权处理商户结算"
            }
            val hash = operationSupport.canonicalHash(
                merchantId, command.merchantSettlementId.toString(), executionId, executionChannelId,
            )
            val existingOperation = Mediator.repositories.findOne(
                SOperation.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.commandType eq COMMAND_TYPE) and
                        (schema.idempotencyKey eq key)
                }
            )
            if (existingOperation != null && existingOperation.canonicalRequestHash != hash) {
                throw MerchantSettlementConflictException(
                    "IDEMPOTENCY_CONFLICT",
                    "幂等键已绑定到内容不同的结算执行",
                    mapOf("operationId" to existingOperation.id.toString(), "idempotencyKey" to key),
                )
            }
            val existingIdentitySettlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicate { schema ->
                    schema.joinSettlementExecutionAttempts().executionId eq executionId
                }
            )
            if (existingIdentitySettlement != null) {
                val prior = existingIdentitySettlement.settlementExecutionAttempts.first { it.executionId == executionId }
                if (existingIdentitySettlement.merchantId != merchantId ||
                    existingIdentitySettlement.id != command.merchantSettlementId ||
                    prior.channelId != executionChannelId || prior.idempotencyKey != key
                ) {
                    throw MerchantSettlementConflictException(
                        "SETTLEMENT_EXECUTION_IDENTITY_CONFLICT",
                        "executionId 已绑定到另一结算执行请求",
                        mapOf("executionId" to executionId, "settlementId" to existingIdentitySettlement.id.toString()),
                    )
                }
            }
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(command.merchantSettlementId)
            ) ?: throw MerchantSettlementNotFoundException(command.merchantSettlementId)
            require(settlement.merchantId == merchantId) { "结算单与 merchantId 不匹配" }
            operationSupport.replayOrNull(merchantId, COMMAND_TYPE, key, hash)?.let { operation ->
                val original = settlement.settlementExecutionAttempts.firstOrNull {
                    it.executionId == operation.resourceId
                } ?: error("operation ${operation.id} refers to missing execution ${operation.resourceId}")
                return Response(
                    settlement.id, original.executionId, original.id.toString(), original.executionGroupIdentity,
                    original.requestIdentity,
                    settlement.status.name,
                    original.acceptedAt != null,
                    original.verdictSummary ?: original.rejectionSummary,
                    original.executorScript,
                    original.executorObservation,
                    operationSupport.receipt(operation, replay = true),
                )
            }
            if (existingIdentitySettlement != null) {
                throw MerchantSettlementConflictException(
                    "SETTLEMENT_EXECUTION_IDENTITY_CONFLICT",
                    "executionId 已有执行记录但缺少对应 Operation",
                    mapOf("executionId" to executionId),
                )
            }
            if (settlement.status in setOf(
                    MerchantSettlementStatus.RESULT_UNKNOWN,
                    MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED,
                )
            ) {
                throw MerchantSettlementConflictException(
                    code = "RESULT_UNKNOWN_REEXECUTION_FORBIDDEN",
                    message = "商户结算单 ${settlement.id} 的前次执行结果尚未解决，不能创建新的执行",
                )
            }
            require(settlement.compositionFrozen && settlement.netAmount.signum() > 0 &&
                settlement.status in setOf(MerchantSettlementStatus.CONFIRMED, MerchantSettlementStatus.FAILED)) {
                "结算单 ${settlement.id} 当前状态不能开始执行"
            }
            if (settlement.status == MerchantSettlementStatus.PROCESSING) {
                throw MerchantSettlementConflictException(
                    "SETTLEMENT_EXECUTION_IN_PROGRESS",
                    "商户结算单 ${settlement.id} 的前次执行尚未结束",
                )
            }
            if (settlement.status == MerchantSettlementStatus.FAILED) {
                require(settlement.settlementExecutionAttempts.lastOrNull()?.finalResult in setOf(
                    com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionFinalResult.FAILED,
                    com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionFinalResult.GATEWAY_REJECTED,
                )) { "结算单 ${settlement.id} 只有前一次明确失败后才能重试" }
            }
            require(settlement.executionChannelId == null || settlement.executionChannelId == executionChannelId) {
                "结算单 ${settlement.id} 的执行渠道快照不能改变"
            }
            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq settlement.merchantId) and
                        (schema.channelId eq executionChannelId) and
                        (schema.currency eq settlement.currency) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE)
                }
            ) ?: error("结算单 ${settlement.id} 没有可用渠道配置")
            val requestedAt = LocalDateTime.now(clock)
            val groupIdentity = settlement.executionGroupIdentity ?: "SETTLEMENT:${settlement.id}"
            val requestIdentity = "$groupIdentity:${settlement.settlementExecutionAttempts.size + 1}"
            val attempt = settlement.startExecutionAttempt(
                operatorIdentity = command.operatorIdentity,
                operatorRole = command.operatorRole,
                requestedAt = requestedAt,
                reviewAfterMinutes = configuration.settlementResultReviewAfterMinutes,
                executionGroupIdentity = groupIdentity,
                requestIdentity = requestIdentity,
                executionChannelId = executionChannelId,
                executionId = executionId,
                idempotencyKey = key,
            )
            val transfer = try {
                Mediator.capabilities.call(
                    StartSettlementTransfer.Request(
                        merchantSettlementId = settlement.id,
                        executionAttemptId = attempt.id.toString(),
                        executionId = executionId,
                        merchantId = settlement.merchantId,
                        channelId = executionChannelId,
                        executionGroupIdentity = groupIdentity,
                        requestIdentity = requestIdentity,
                        amount = settlement.netAmount,
                        currency = settlement.currency,
                    )
                )
            } catch (failure: RuntimeException) {
                val diagnostic = "结算资金划拨 provider 调用失败"
                settlement.rejectExecutionStart(attempt.id, "SETTLEMENT_GATEWAY_ERROR", diagnostic)
                return response(settlement, attempt.id.toString(), false, diagnostic, key, hash)
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
                return response(settlement, attempt.id.toString(), false, diagnostic, key, hash)
            }
            settlement.recordExecutorObservation(
                attempt.id, transfer.executorScript, transfer.observation, transfer.diagnosticSummary,
            )
            settlement.markExecutionAccepted(attempt.id, transfer.externalSettlementIdentity, requestedAt)
            when (transfer.observation) {
                "SUCCESS", "FAILURE", "UNKNOWN" -> {
                    val result = if (transfer.observation == "FAILURE") "FAILED" else transfer.observation
                    settlement.recordSettlementResult(
                        attemptId = attempt.id,
                        notificationIdentity = "executor:$executionId",
                        payloadFingerprint = operationSupport.canonicalHash(
                            executionId, transfer.executorScript, transfer.externalSettlementIdentity, result,
                        ),
                        channelId = executionChannelId,
                        executionGroupIdentity = groupIdentity,
                        requestIdentity = requestIdentity,
                        externalSettlementIdentity = transfer.externalSettlementIdentity,
                        amount = settlement.netAmount,
                        currency = settlement.currency,
                        result = result,
                        resultCode = "REFERENCE_EXECUTOR_${transfer.observation}",
                        occurredAt = requestedAt,
                        receivedAt = requestedAt,
                        verified = true,
                        verificationSummary = transfer.diagnosticSummary,
                    )
                }
                "NO_RESULT" -> settlement.markExecutionNoResult(attempt.id)
                else -> error("不支持的 executor observation：${transfer.observation}")
            }
            return response(settlement, attempt.id.toString(), true, transfer.diagnosticSummary, key, hash)
        }

        private fun response(
            settlement: com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement,
            attemptId: String,
            accepted: Boolean,
            diagnostic: String?,
            idempotencyKey: String,
            hash: String,
        ): Response = Response(
            merchantSettlementId = settlement.id,
            executionId = settlement.settlementExecutionAttempts.last().executionId,
            attemptId = attemptId,
            executionGroupIdentity = settlement.executionGroupIdentity,
            requestIdentity = settlement.settlementExecutionAttempts.lastOrNull()?.requestIdentity,
            status = settlement.status.name,
            providerAccepted = accepted,
            diagnosticSummary = diagnostic,
            executorScript = settlement.settlementExecutionAttempts.last().executorScript,
            executorObservation = settlement.settlementExecutionAttempts.last().executorObservation,
            receipt = operationSupport.accept(
                settlement.merchantId, COMMAND_TYPE, idempotencyKey, hash,
                "SettlementExecution", settlement.settlementExecutionAttempts.last().executionId,
                "/api/merchant-settlements/${settlement.id}",
            ),
        )
    }

    data class Request(
        /**
         * 结算标识
         */
        val merchantSettlementId: MerchantSettlementId,
        val merchantId: String,
        val executionId: String,
        /**
         * 操作员身份
         */
        val operatorIdentity: String,
        /**
         * 操作员角色
         */
        val operatorRole: String,
        /** 执行渠道是一次执行的 evidence；不属于 settlement scope。 */
        val executionChannelId: String,
        val idempotencyKey: String,
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
        val executionId: String,
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
        val diagnosticSummary: String?,
        val executorScript: String,
        val executorObservation: String,
        val receipt: OperationReceipt,
    )
    private const val COMMAND_TYPE = "StartMerchantSettlementExecution"
}
