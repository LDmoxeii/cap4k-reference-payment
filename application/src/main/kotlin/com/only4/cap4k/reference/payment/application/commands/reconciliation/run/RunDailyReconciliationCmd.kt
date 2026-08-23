package com.only4.cap4k.reference.payment.application.commands.reconciliation.run

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.PullChannelStatement
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.platform.LoadPlatformReconciliationFacts
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.appendReconciliationRun
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.markStatementFetchFailed
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationBatchStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.factory.ReconciliationBatchFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "RunDailyReconciliation",
    packageName = "reconciliation.run",
    description = "Idempotently create or load a daily batch, pull a statement revision, load platform facts, and reconcile it",
    aggregates = ["ReconciliationBatch"],
    family = "command"
)
object RunDailyReconciliationCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {
        /**
         * ordinary scheduler 只负责发送该 Command。Handler 以 channel/currency/business-date 幂等装载批次，
         * 再分别调用权威账单 Pull 与平台事实投影；任一 provider 失败都保存受控中文阻断摘要，
         * 原始异常只写日志，不能把失败伪装成空账单完成。
         */
        override fun handle(command: Request): Response {
            val channelId = command.channelId.trim()
            val currency = command.currency.trim().uppercase()
            val timezone = BUSINESS_TIMEZONE
            require(channelId.isNotBlank()) { "渠道身份不能为空" }
            require(currency.isNotBlank()) { "币种不能为空" }
            val zone = ZoneId.of(timezone)
            val reconciliationDate = command.triggeredAt.atZone(zone).toLocalDate().minusDays(1)

            val existing = findBatch(channelId, currency, reconciliationDate)
            val batch = existing ?: Mediator.factories.create<ReconciliationBatchFactory.Payload, ReconciliationBatch>(
                ReconciliationBatchFactory.Payload(
                    channelId = channelId,
                    currency = currency,
                    reconciliationDate = reconciliationDate,
                    businessTimezone = timezone,
                    status = ReconciliationBatchStatus.PENDING,
                    currentEffectiveRunId = null,
                    statementWaitDeadlineAt = LocalDateTime.ofInstant(
                        reconciliationDate.plusDays(2).atStartOfDay(zone).toInstant(),
                        ZoneOffset.UTC,
                    ),
                    blockingReason = "等待渠道账单",
                    completedAt = null,
                )
            )

            val statement = try {
                Mediator.capabilities.call(
                    PullChannelStatement.Request(channelId, currency, reconciliationDate, timezone)
                ).statement
            } catch (failure: RuntimeException) {
                return failureResponse(batch, existing != null, failure, command.triggeredAt)
            }
            val facts = try {
                Mediator.capabilities.call(
                    LoadPlatformReconciliationFacts.Request(channelId, currency, reconciliationDate, timezone)
                ).facts
            } catch (failure: RuntimeException) {
                return failureResponse(batch, existing != null, failure, command.triggeredAt)
            }

            val result = batch.appendReconciliationRun(
                statement,
                facts,
                LocalDateTime.ofInstant(command.triggeredAt, ZoneOffset.UTC),
            )
            return Response(
                batchId = batch.id.toString(),
                runId = result.run.id.toString(),
                batchStatus = batch.status.name,
                idempotentReplay = result.idempotentReplay,
                unresolvedDifferenceCount = batch.unresolvedDifferenceCount,
                blockingReason = batch.blockingReason,
            )
        }

        private fun failureResponse(
            batch: ReconciliationBatch,
            existing: Boolean,
            failure: RuntimeException,
            triggeredAt: Instant,
        ): Response {
            val failedAt = LocalDateTime.ofInstant(triggeredAt, ZoneOffset.UTC)
            log.warn("日终对账拉取账单或平台事实失败：batchId={}", batch.id, failure)
            batch.markStatementFetchFailed(failedAt, "渠道账单或平台资金事实暂时不可用")
            return Response(
                batchId = batch.id.toString(),
                runId = null,
                batchStatus = batch.status.name,
                idempotentReplay = existing,
                unresolvedDifferenceCount = batch.unresolvedDifferenceCount,
                blockingReason = batch.blockingReason,
            )
        }
    }

    data class Request(
        val channelId: String,
        val currency: String,
        val triggeredAt: Instant
    ) : Command<Response>

    data class Response(
        val batchId: String,
        val runId: String?,
        val batchStatus: String,
        val idempotentReplay: Boolean,
        val unresolvedDifferenceCount: Int,
        val blockingReason: String?
    )

    private const val BUSINESS_TIMEZONE = "Asia/Shanghai"
    private val log = LoggerFactory.getLogger(RunDailyReconciliationCmd::class.java)

    private fun findBatch(channelId: String, currency: String, date: LocalDate): ReconciliationBatch? =
        Mediator.repositories.findOne(
            SReconciliationBatch.predicate { schema ->
                (schema.channelId eq channelId) and
                    (schema.currency eq currency) and
                    (schema.reconciliationDate eq date)
            }
        )
}
