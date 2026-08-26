package com.only4.cap4k.reference.payment.application.commands.reconciliation.integration

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
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationBatchStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.factory.ReconciliationBatchFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ProcessAvailableChannelStatement",
    packageName = "reconciliation.integration",
    description = "Pull and reconcile the exact statement revision announced by an inbound availability event",
    aggregates = ["ReconciliationBatch"],
    family = "command"
)
object ProcessAvailableChannelStatementCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {

        /**
         * 入站事件只声明某个 statement revision 已可获取，不携带账单正文。
         * Handler 仍通过 PullChannelStatement 取得权威数据，并与 scheduler/manual rerun 共享 batch/run 唯一性；
         * 重放 event 或迟到旧 revision 都不能创建第二套对账状态机或回退 effective pointer。
         */
        override fun handle(command: Request): Response {
            val eventIdentity = command.eventIdentity.trim()
            val channelId = command.channelId.trim()
            val currency = command.currency.trim().uppercase()
            val statementIdentity = command.statementIdentity.trim()
            val statementRevision = command.statementRevision.trim()
            require(eventIdentity.isNotBlank()) { "事件身份不能为空" }
            require(channelId.isNotBlank()) { "渠道身份不能为空" }
            require(currency.isNotBlank()) { "币种不能为空" }
            require(statementIdentity.isNotBlank()) { "账单身份不能为空" }
            require(statementRevision.matches(POSITIVE_REVISION)) {
                "statementRevision 必须为正整数"
            }

            val timezone = BUSINESS_TIMEZONE
            val zone = ZoneId.of(timezone)
            val existing = findBatch(channelId, currency, command.reconciliationDate)
            existing?.reconciliationRuns
                ?.firstOrNull {
                    it.statementIdentity == statementIdentity && it.statementRevision == statementRevision
                }
                ?.let { run ->
                    return Response(
                        batchId = existing.id.toString(),
                        runId = run.id.toString(),
                        batchStatus = existing.status.name,
                        idempotentReplay = true,
                    )
                }

            val batch = existing ?: Mediator.factories.create<ReconciliationBatchFactory.Payload, ReconciliationBatch>(
                ReconciliationBatchFactory.Payload(
                    channelId = channelId,
                    currency = currency,
                    reconciliationDate = command.reconciliationDate,
                    businessTimezone = timezone,
                    status = ReconciliationBatchStatus.PENDING,
                    currentEffectiveRunId = null,
                    statementWaitDeadlineAt = LocalDateTime.ofInstant(
                        command.reconciliationDate.plusDays(2).atStartOfDay(zone).toInstant(),
                        ZoneOffset.UTC,
                    ),
                    blockingReason = "等待渠道账单",
                    completedAt = null,
                )
            )

            val statement = Mediator.capabilities.call(
                PullChannelStatement.Request(channelId, currency, command.reconciliationDate, timezone)
            ).statement
            require(statement.statementIdentity == statementIdentity) {
                "实际拉取的账单身份 ${statement.statementIdentity} 与事件声明的 $statementIdentity 不一致"
            }
            require(statement.statementRevision.matches(POSITIVE_REVISION)) {
                "pulled 账单 revision 必须为正整数"
            }
            require(compareStatementRevision(statement.statementRevision, statementRevision) >= 0) {
                "实际拉取的账单 revision ${statement.statementRevision} 早于事件声明的 $statementRevision"
            }

            val facts = Mediator.capabilities.call(
                LoadPlatformReconciliationFacts.Request(channelId, currency, command.reconciliationDate, timezone)
            ).facts
            val result = batch.appendReconciliationRun(
                statement = statement,
                platformFacts = facts,
                startedAt = LocalDateTime.ofInstant(command.publishedAt, ZoneOffset.UTC),
            )
            return Response(
                batchId = batch.id.toString(),
                runId = result.run.id.toString(),
                batchStatus = batch.status.name,
                idempotentReplay = result.idempotentReplay,
            )
        }
    }

    data class Request(
        /**
         * 身份
         */
        val eventIdentity: String,
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 对账日期
         */
        val reconciliationDate: LocalDate,
        /**
         * 对账单身份
         */
        val statementIdentity: String,
        /**
         * 对账单版本
         */
        val statementRevision: String,
        /**
         * 发布时间
         */
        val publishedAt: Instant,
        /**
         * 关联身份
         */
        val correlationIdentity: String?,
        /**
         * 因果身份
         */
        val causationIdentity: String?
    ) : Command<Response>

    data class Response(
        /**
         * 批次标识
         */
        val batchId: String,
        /**
         * 运行标识
         */
        val runId: String,
        /**
         * 批次状态
         */
        val batchStatus: String,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean
    )

    private const val BUSINESS_TIMEZONE = "Asia/Shanghai"
    private val POSITIVE_REVISION = Regex("[1-9][0-9]*")

    private fun compareStatementRevision(left: String, right: String): Int =
        left.toBigInteger().compareTo(right.toBigInteger())

    private fun findBatch(channelId: String, currency: String, date: LocalDate): ReconciliationBatch? =
        Mediator.repositories.findOne(
            SReconciliationBatch.predicate { schema ->
                (schema.channelId eq channelId) and
                    (schema.currency eq currency) and
                    (schema.reconciliationDate eq date)
            }
        )
}
