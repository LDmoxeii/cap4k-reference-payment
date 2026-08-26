package com.only4.cap4k.reference.payment.application.commands.reconciliation.run

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.PullChannelStatement
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.platform.LoadPlatformReconciliationFacts
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.appendReconciliationRun
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.markStatementFetchFailed
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
    class Handler : CommandHandler<Request, Response> {
        /**
         * 显式重跑沿用既有 batch scope，并重新 Pull 权威账单与平台事实；相同 revision 由聚合幂等复用，
         * 新 revision 追加历史。provider 原始失败仅写日志，查询侧只保存稳定中文阻断摘要。
         */
        override fun handle(command: Request): Response {
            require(command.requestedBy.isNotBlank()) { "请求操作员不能为空" }
            val batch = Mediator.repositories.findOne(
                SReconciliationBatch.predicateById(ReconciliationBatchId.parse(command.batchId))
            ) ?: throw ReconciliationBatchNotFoundException(command.batchId)
            val statement = try {
                Mediator.capabilities.call(
                    PullChannelStatement.Request(
                        batch.channelId, batch.currency, batch.reconciliationDate, batch.businessTimezone
                    )
                ).statement
            } catch (failure: RuntimeException) {
                log.warn("对账批次重跑失败：batchId={}", batch.id, failure)
                batch.markStatementFetchFailed(
                    LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC),
                    "渠道账单或平台资金事实暂时不可用",
                )
                return failureResponse(batch.id.toString(), batch.status.name)
            }
            val facts = try {
                Mediator.capabilities.call(
                    LoadPlatformReconciliationFacts.Request(
                        batch.channelId, batch.currency, batch.reconciliationDate, batch.businessTimezone
                    )
                ).facts
            } catch (failure: RuntimeException) {
                log.warn("对账批次重跑失败：batchId={}", batch.id, failure)
                batch.markStatementFetchFailed(
                    LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC),
                    "渠道账单或平台资金事实暂时不可用",
                )
                return failureResponse(batch.id.toString(), batch.status.name)
            }

            val result = batch.appendReconciliationRun(
                statement,
                facts,
                LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC),
            )
            return Response(
                batchId = batch.id.toString(),
                runId = result.run.id.toString(),
                batchStatus = batch.status.name,
                idempotentReplay = result.idempotentReplay,
                statementIdentity = result.run.statementIdentity,
                statementRevision = result.run.statementRevision,
            )
        }

        private fun failureResponse(batchId: String, batchStatus: String) = Response(
            batchId = batchId,
            runId = null,
            batchStatus = batchStatus,
            idempotentReplay = false,
            statementIdentity = null,
            statementRevision = null,
        )
    }

    private val log = LoggerFactory.getLogger(RerunReconciliationBatchCmd::class.java)

    data class Request(
        /**
         * 批次标识
         */
        val batchId: String,
        /**
         * 请求操作人
         */
        val requestedBy: String,
        /**
         * 请求时间
         */
        val requestedAt: Instant
    ) : Command<Response>

    data class Response(
        /**
         * 批次标识
         */
        val batchId: String,
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
        val statementRevision: String?
    )
}
