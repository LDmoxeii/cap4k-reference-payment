package com.only4.cap4k.reference.payment.application.commands.reconciliation.run

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.PullChannelStatement
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.ChannelStatementUnavailableException
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.platform.LoadPlatformReconciliationFacts
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.application.manual_review.openReconciliationDifferenceReview
import com.only4.cap4k.reference.payment.application.manual_review.openStatementFetchFailureReview
import com.only4.cap4k.reference.payment.application.commands.merchant_notification.MerchantNotificationService
import com.only4.cap4k.reference.payment.application.commands.merchant_notification.notifyCompletedReconciliationRun
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.appendReconciliationRun
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "RerunReconciliationBatch",
    packageName = "reconciliation.run",
    description = "Explicitly pull and process a statement revision for an existing reconciliation batch",
    aggregates = ["ReconciliationBatch"],
    family = "command"
)
object RerunReconciliationBatchCmd {

    @Service
    class Handler(
        private val manualReviewSupport: ManualReviewSupport,
        private val notifications: MerchantNotificationService,
        private val operationSupport: OperationSupport,
    ) : CommandHandler<Request, Response> {
        /**
         * 显式重跑沿用既有 batch scope，并重新 Pull 权威账单与平台事实；相同 revision 由聚合幂等复用，
         * 新 revision 追加历史。显式重跑的 provider 同步失败以稳定错误拒绝，不创建 Operation。
         */
        override fun handle(command: Request): Response {
            require(command.requestedBy.isNotBlank()) { "请求操作员不能为空" }
            val idempotencyKey = command.idempotencyKey.trim()
            require(idempotencyKey.isNotBlank()) { "幂等键不能为空" }
            val batch = Mediator.repositories.findOne(
                SReconciliationBatch.predicateById(command.reconciliationBatchId)
            ) ?: throw ReconciliationBatchNotFoundException(command.reconciliationBatchId)
            command.sourceRunId?.let { sourceId ->
                require(batch.reconciliationRuns.any { it.id.toString() == sourceId.trim() }) {
                    "指定对账运行不属于当前对账批次"
                }
            }
            val requestHash = operationSupport.canonicalHash(
                batch.id.toString(), command.sourceRunId?.trim(), command.requestedBy.trim(),
            )
            operationSupport.replayOrNull(OPERATION_SCOPE, COMMAND_TYPE, idempotencyKey, requestHash)?.let { operation ->
                val run = batch.reconciliationRuns.firstOrNull { it.id.toString() == operation.resourceId }
                    ?: throw IllegalStateException("Operation ${operation.id} 引用的对账运行不存在")
                return Response(
                    reconciliationBatchId = batch.id,
                    runId = run.id.toString(),
                    batchStatus = batch.status.name,
                    idempotentReplay = true,
                    statementIdentity = run.statementIdentity,
                    statementRevision = run.statementRevision,
                    receipt = operationSupport.receipt(operation, replay = true),
                )
            }
            val statement = try {
                Mediator.capabilities.call(
                    PullChannelStatement.Request(
                        batch.channelId,
                        batch.currency,
                        batch.reconciliationDate,
                        batch.businessTimezone,
                        batch.currentEffectiveRunId?.let { currentId ->
                            batch.reconciliationRuns.firstOrNull { it.id.toString() == currentId }?.statementIdentity
                        },
                    )
                ).statement
            } catch (failure: ChannelStatementUnavailableException) {
                log.warn("对账批次重跑失败：batchId={}", batch.id, failure)
                throw unavailable()
            }
            val facts = try {
                Mediator.capabilities.call(
                    LoadPlatformReconciliationFacts.Request(
                        batch.channelId, batch.currency, batch.reconciliationDate, batch.businessTimezone
                    )
                ).facts
            } catch (failure: RuntimeException) {
                log.warn("对账批次重跑失败：batchId={}", batch.id, failure)
                throw unavailable()
            }

            require(statement.channelId == batch.channelId) { "账单渠道不属于当前对账批次" }
            require(statement.currency == batch.currency) { "账单币种不属于当前对账批次" }
            require(statement.reconciliationDate == batch.reconciliationDate) { "账单业务日不属于当前对账批次" }
            val result = batch.appendReconciliationRun(
                statement,
                facts,
                LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC),
            )
            if (!result.idempotentReplay) {
                result.run.reconciliationItems
                    .filter { it.settlementBlocked }
                    .forEach { manualReviewSupport.openReconciliationDifferenceReview(batch, result.run, it) }
                notifications.notifyCompletedReconciliationRun(batch, result.run)
            }
            return Response(
                reconciliationBatchId = batch.id,
                runId = result.run.id.toString(),
                batchStatus = batch.status.name,
                idempotentReplay = result.idempotentReplay,
                statementIdentity = result.run.statementIdentity,
                statementRevision = result.run.statementRevision,
                receipt = operationSupport.accept(
                    merchantId = OPERATION_SCOPE,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = idempotencyKey,
                    canonicalRequestHash = requestHash,
                    resourceType = "ReconciliationRun",
                    resourceId = result.run.id.toString(),
                    resourceUrl = "/api/reconciliation-runs/${result.run.id}",
                ),
            )
        }

        private fun unavailable() = PaymentConflictException(
            code = "STATEMENT_UNAVAILABLE",
            message = "渠道账单或平台资金事实暂时不可用",
        )
    }

    private val log = LoggerFactory.getLogger(RerunReconciliationBatchCmd::class.java)

    data class Request(
        /**
         * 批次标识
         */
        val reconciliationBatchId: ReconciliationBatchId,
        /**
         * 请求操作人
         */
        val requestedBy: String,
        val idempotencyKey: String,
        val sourceRunId: String? = null,
        /**
         * 请求时间
         */
        val requestedAt: Instant
    ) : Command<Response>

    data class Response(
        /**
         * 批次标识
         */
        val reconciliationBatchId: ReconciliationBatchId,
        /**
         * 运行标识
         */
        val runId: String?,
        /**
         * 批次状态
         */
        val batchStatus: String,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean,
        /**
         * 对账单身份
         */
        val statementIdentity: String?,
        /**
         * 对账单版本
         */
        val statementRevision: String?,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "RerunReconciliationRun"
    private const val OPERATION_SCOPE = "reference-reconciliation"
}
