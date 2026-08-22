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

        override fun handle(command: Request): Response {
            val eventIdentity = command.eventIdentity.trim()
            val channelId = command.channelId.trim()
            val currency = command.currency.trim().uppercase()
            val statementIdentity = command.statementIdentity.trim()
            val statementRevision = command.statementRevision.trim()
            require(eventIdentity.isNotBlank()) { "eventIdentity must not be blank" }
            require(channelId.isNotBlank()) { "channelId must not be blank" }
            require(currency.isNotBlank()) { "currency must not be blank" }
            require(statementIdentity.isNotBlank()) { "statementIdentity must not be blank" }
            require(statementRevision.matches(POSITIVE_REVISION)) {
                "statementRevision must be a positive integer"
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
                    blockingReason = "Awaiting channel statement",
                    completedAt = null,
                )
            )

            val statement = Mediator.capabilities.call(
                PullChannelStatement.Request(channelId, currency, command.reconciliationDate, timezone)
            ).statement
            require(statement.statementIdentity == statementIdentity) {
                "pulled statement identity ${statement.statementIdentity} does not match announced $statementIdentity"
            }
            require(statement.statementRevision.matches(POSITIVE_REVISION)) {
                "pulled statement revision must be a positive integer"
            }
            require(compareStatementRevision(statement.statementRevision, statementRevision) >= 0) {
                "pulled statement revision ${statement.statementRevision} is older than announced $statementRevision"
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
        val eventIdentity: String,
        val channelId: String,
        val currency: String,
        val reconciliationDate: LocalDate,
        val statementIdentity: String,
        val statementRevision: String,
        val publishedAt: Instant,
        val correlationIdentity: String?,
        val causationIdentity: String?
    ) : Command<Response>

    data class Response(
        val batchId: String,
        val runId: String,
        val batchStatus: String,
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
