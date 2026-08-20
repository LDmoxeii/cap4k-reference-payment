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
        override fun handle(command: Request): Response {
            require(command.requestedBy.isNotBlank()) { "requestedBy must not be blank" }
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
                val reason = failure.message?.takeIf { it.isNotBlank() }
                    ?: failure::class.simpleName
                    ?: "Statement provider failure"
                batch.markStatementFetchFailed(LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC), reason)
                return failureResponse(batch.id.toString(), batch.status.name)
            }
            val facts = try {
                Mediator.capabilities.call(
                    LoadPlatformReconciliationFacts.Request(
                        batch.channelId, batch.currency, batch.reconciliationDate, batch.businessTimezone
                    )
                ).facts
            } catch (failure: RuntimeException) {
                val reason = failure.message?.takeIf { it.isNotBlank() }
                    ?: failure::class.simpleName
                    ?: "Statement provider failure"
                batch.markStatementFetchFailed(LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC), reason)
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

    data class Request(
        val batchId: String,
        val requestedBy: String,
        val requestedAt: Instant
    ) : Command<Response>

    data class Response(
        val batchId: String,
        val runId: String?,
        val batchStatus: String,
        val idempotentReplay: Boolean,
        val statementIdentity: String?,
        val statementRevision: String?
    )
}
