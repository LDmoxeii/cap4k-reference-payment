package com.only4.cap4k.reference.payment.application.commands.reconciliation.disposition

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
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
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
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
                confirmationFor(batch, item, command, disposedAt)
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
            batch: ReconciliationBatch,
            item: ReconciliationItem,
            command: Request,
            disposedAt: LocalDateTime,
        ): ReconciliationConfirmationFactCreation {
            val amount = item.channelAmount ?: throw IllegalArgumentException("Confirmation requires channel amount evidence")
            val currency = item.channelCurrency ?: throw IllegalArgumentException("Confirmation requires channel currency evidence")
            val externalIdentity = item.channelTransactionIdentity
                ?: throw IllegalArgumentException("Confirmation requires a channel transaction identity")
            val attribution = resolveAttribution(batch, item, command)
            return ReconciliationConfirmationFactCreation(
                sourceDifferenceIdentity = item.differenceIdentity,
                merchantId = attribution.merchantId,
                channelId = attribution.channelId,
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

        private fun resolveAttribution(
            batch: ReconciliationBatch,
            item: ReconciliationItem,
            command: Request,
        ): ConfirmationAttribution {
            var merchantId: String? = null
            var channelId: String? = null

            item.paymentId?.let { rawPaymentId ->
                val payment = Mediator.repositories.findOne(
                    SPayment.predicateById(PaymentId.parse(rawPaymentId))
                ) ?: throw IllegalArgumentException("Confirmation payment $rawPaymentId does not exist")
                merchantId = payment.merchantId
                item.paymentAttemptId?.let { rawAttemptId ->
                    val attempt = payment.attempts.firstOrNull { it.id.toString() == rawAttemptId }
                        ?: throw IllegalArgumentException(
                            "Confirmation payment attempt $rawAttemptId does not belong to payment $rawPaymentId"
                        )
                    channelId = attempt.channelId
                }
            }

            item.refundId?.let { rawRefundId ->
                val refund = Mediator.repositories.findOne(
                    SRefund.predicateById(RefundId.parse(rawRefundId))
                ) ?: throw IllegalArgumentException("Confirmation refund $rawRefundId does not exist")
                item.paymentId?.let { referencedPaymentId ->
                    require(refund.paymentId.toString() == referencedPaymentId) {
                        "Confirmation refund $rawRefundId does not belong to payment $referencedPaymentId"
                    }
                }
                item.refundAttemptId?.let { rawAttemptId ->
                    require(refund.attempts.any { it.id.toString() == rawAttemptId }) {
                        "Confirmation refund attempt $rawAttemptId does not belong to refund $rawRefundId"
                    }
                }
                merchantId?.let { require(it == refund.merchantId) { "Confirmation weak references disagree on merchant" } }
                channelId?.let { require(it == refund.channelId) { "Confirmation weak references disagree on channel" } }
                merchantId = refund.merchantId
                channelId = refund.channelId
            }

            val explicitMerchantId = command.merchantId?.trim()?.takeIf { it.isNotBlank() }
            val explicitChannelId = command.channelId?.trim()?.takeIf { it.isNotBlank() }
            if (merchantId == null) {
                merchantId = explicitMerchantId
                    ?: throw IllegalArgumentException("Confirmation requires merchantId when weak references do not provide it")
            } else {
                explicitMerchantId?.let {
                    require(it == merchantId) { "Explicit confirmation merchant does not match weak references" }
                }
            }
            if (channelId == null) {
                channelId = explicitChannelId
                    ?: throw IllegalArgumentException("Confirmation requires channelId when weak references do not provide it")
            } else {
                explicitChannelId?.let {
                    require(it == channelId) { "Explicit confirmation channel does not match weak references" }
                }
            }

            require(channelId == batch.channelId) { "Confirmation channel does not belong to reconciliation batch" }
            return ConfirmationAttribution(
                merchantId = requireNotNull(merchantId),
                channelId = requireNotNull(channelId),
            )
        }
    }

    private data class ConfirmationAttribution(
        val merchantId: String,
        val channelId: String,
    )

    data class Request(
        val batchId: String,
        val itemId: String,
        val merchantId: String?,
        val channelId: String?,
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
