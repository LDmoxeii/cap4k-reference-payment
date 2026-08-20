package com.only4.cap4k.reference.payment.application.commands.reconciliation.disposition

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationConfirmationFactCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationDispositionCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationItem
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.appendDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.recalculateCompletion
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.DispositionAuthorization
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionConclusion
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.SettlementImpact
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "DisposeReconciliationDifference",
    packageName = "reconciliation.disposition",
    description = "Authorize and append a disposition, optionally forming a separate confirmation fact",
    aggregates = ["ReconciliationBatch"],
    family = "command"
)
object DisposeReconciliationDifferenceCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            require(command.operatorIdentity.isNotBlank()) { "operatorIdentity must not be blank" }
            require(command.evidence.isNotBlank()) { "evidence must not be blank" }
            val batch = Mediator.repositories.findOne(
                SReconciliationBatch.predicateById(ReconciliationBatchId.parse(command.batchId))
            ) ?: throw ReconciliationBatchNotFoundException(command.batchId)
            val item = batch.reconciliationRuns.asSequence()
                .flatMap { it.reconciliationItems.asSequence() }
                .firstOrNull { it.id.toString() == command.itemId }
                ?: throw IllegalArgumentException("Reconciliation item ${command.itemId} was not found in batch ${command.batchId}")
            val disposedAt = LocalDateTime.ofInstant(command.disposedAt, ZoneOffset.UTC)
            val authorized = command.operatorRole.trim().uppercase() == AUTHORIZED_OPERATOR_ROLE
            val conclusion = if (authorized) enumValue<ReconciliationDispositionConclusion>(command.conclusion, "conclusion") else null
            val impact = enumValue<SettlementImpact>(command.settlementImpact, "settlementImpact")

            val confirmation = if (authorized && conclusion == ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT) {
                confirmationFor(item, command, disposedAt)
            } else null
            val disposition = batch.appendDisposition(
                differenceIdentity = item.differenceIdentity,
                creation = ReconciliationDispositionCreation(
                    operatorIdentity = command.operatorIdentity.trim(),
                    operatorRole = command.operatorRole.trim(),
                    authorizationResult = if (authorized) DispositionAuthorization.AUTHORIZED else DispositionAuthorization.DENIED,
                    status = if (authorized) ReconciliationDispositionStatus.APPLIED else ReconciliationDispositionStatus.REJECTED,
                    conclusion = conclusion,
                    settlementImpact = impact,
                    evidence = command.evidence.trim(),
                    followUp = command.followUp?.trim()?.takeIf { it.isNotBlank() },
                    disposedAt = disposedAt,
                ),
                confirmation = confirmation,
            )
            batch.recalculateCompletion(disposedAt)
            val confirmationFact = item.reconciliationConfirmationFacts.lastOrNull()
                ?.takeIf { confirmation != null && it.sourceDifferenceIdentity == item.differenceIdentity }

            return Response(
                dispositionId = disposition.id.toString(),
                authorization = disposition.authorizationResult.name,
                status = disposition.status.name,
                confirmationFactId = confirmationFact?.id?.toString(),
                batchStatus = batch.status.name,
                settlementBlocked = batch.settlementBlocked,
                blockingReason = batch.blockingReason,
            )
        }

        private fun confirmationFor(
            item: ReconciliationItem,
            command: Request,
            disposedAt: LocalDateTime,
        ): ReconciliationConfirmationFactCreation {
            val amount = item.channelAmount ?: throw IllegalArgumentException("Confirmation requires channel amount evidence")
            val currency = item.channelCurrency ?: throw IllegalArgumentException("Confirmation requires channel currency evidence")
            val externalIdentity = item.channelTransactionIdentity
                ?: throw IllegalArgumentException("Confirmation requires a channel transaction identity")
            return ReconciliationConfirmationFactCreation(
                sourceDifferenceIdentity = item.differenceIdentity,
                operatorIdentity = command.operatorIdentity.trim(),
                confirmationReason = command.followUp?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Authorized reconciliation disposition",
                evidence = command.evidence.trim(),
                transactionKind = item.transactionKind,
                amount = amount,
                currency = currency,
                externalTransactionIdentity = externalIdentity,
                paymentId = item.paymentId,
                refundId = item.refundId,
                confirmedAt = disposedAt,
            )
        }
    }

    data class Request(
        val batchId: String,
        val itemId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val conclusion: String,
        val settlementImpact: String,
        val evidence: String,
        val followUp: String?,
        val disposedAt: Instant
    ) : Command<Response>

    data class Response(
        val dispositionId: String,
        val authorization: String,
        val status: String,
        val confirmationFactId: String?,
        val batchStatus: String,
        val settlementBlocked: Boolean,
        val blockingReason: String?
    )

    private const val AUTHORIZED_OPERATOR_ROLE = "RECONCILIATION_OPERATOR"

    private inline fun <reified E : Enum<E>> enumValue(value: String, field: String): E =
        try {
            enumValueOf<E>(value.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported $field: $value")
        }
}
