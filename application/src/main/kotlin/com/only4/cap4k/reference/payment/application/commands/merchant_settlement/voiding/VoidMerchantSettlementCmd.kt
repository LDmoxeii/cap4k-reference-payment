package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.voiding

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementLineCreation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.factory.MerchantSettlementFactory
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.linkReplacement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.requestActivation
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.voidBeforeExecution
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "VoidMerchantSettlement",
    packageName = "merchant_settlement.voiding",
    description = "Void a settlement before external execution and optionally create a replacement chain",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object VoidMerchantSettlementCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(MerchantSettlementId.parse(command.settlementId))
            ) ?: throw MerchantSettlementNotFoundException(command.settlementId)
            settlement.voidBeforeExecution(
                operatorIdentity = command.operatorIdentity,
                operatorRole = command.operatorRole,
                reason = command.reason,
                voidedAt = LocalDateTime.ofInstant(command.voidedAt, ZoneOffset.UTC),
            )
            val replacement = if (command.createReplacement) createReplacement(settlement) else null
            if (replacement != null) settlement.linkReplacement(replacement.id)
            return Response(settlement.id.toString(), settlement.status.name, replacement?.id?.toString())
        }

        private fun createReplacement(previous: MerchantSettlement): MerchantSettlement {
            val replacement = Mediator.factories.create<MerchantSettlementFactory.Payload, MerchantSettlement>(
                MerchantSettlementFactory.Payload(
                    merchantId = previous.merchantId,
                    channelId = previous.channelId,
                    currency = previous.currency,
                    periodType = previous.periodType,
                    periodStart = previous.periodStart,
                    periodEnd = previous.periodEnd,
                    businessTimezone = previous.businessTimezone,
                    scopeIdentity = previous.scopeIdentity,
                    effectiveScopeIdentity = null,
                    status = if (previous.netAmount.signum() < 0) {
                        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED
                    } else {
                        MerchantSettlementStatus.PREPARED
                    },
                    eligibleCount = previous.eligibleCount,
                    excludedCount = previous.excludedCount,
                    blockerSummary = previous.blockerSummary,
                    paymentGrossAmount = previous.paymentGrossAmount,
                    refundGrossAmount = previous.refundGrossAmount,
                    feeTotalAmount = previous.feeTotalAmount,
                    adjustmentTotalAmount = previous.adjustmentTotalAmount,
                    netAmount = previous.netAmount,
                    compositionFrozen = false,
                    executionGroupIdentity = null,
                    predecessorSettlementId = previous.id.toString(),
                    replacementSettlementId = null,
                    confirmedBy = null,
                    confirmedAt = null,
                    voidedBy = null,
                    voidReason = null,
                    voidedAt = null,
                    settledFactFormed = false,
                    externalSettlementIdentity = null,
                    completedAt = null,
                    lastRejectionSummary = null,
                    lastConflictSummary = null,
                    lastReviewSummary = null,
                    settlementLines = previous.settlementLines.map { line ->
                        SettlementLineCreation(
                            lineIdentity = line.lineIdentity,
                            sourceKind = line.sourceKind,
                            transactionKind = line.transactionKind,
                            sourceFactIdentity = line.sourceFactIdentity,
                            effectiveConsumptionIdentity = null,
                            feeFactIdentity = line.feeFactIdentity,
                            paymentId = line.paymentId,
                            paymentAttemptId = line.paymentAttemptId,
                            refundId = line.refundId,
                            refundAttemptId = line.refundAttemptId,
                            reconciliationBatchId = line.reconciliationBatchId,
                            reconciliationRunId = line.reconciliationRunId,
                            reconciliationItemId = line.reconciliationItemId,
                            reconciliationConfirmationFactId = line.reconciliationConfirmationFactId,
                            externalTransactionIdentity = line.externalTransactionIdentity,
                            grossAmount = line.grossAmount,
                            feeAmount = line.feeAmount,
                            signedNetAmount = line.signedNetAmount,
                            currency = line.currency,
                            occurredAt = line.occurredAt,
                            recordedAt = line.recordedAt,
                            feeBasisPoints = line.feeBasisPoints,
                            feeFixedAmount = line.feeFixedAmount,
                            feeRoundingMode = line.feeRoundingMode,
                            feeCurrencyPrecision = line.feeCurrencyPrecision,
                            feeCalculationAmount = line.feeCalculationAmount,
                            eligibilityBasis = line.eligibilityBasis,
                            confirmationReason = line.confirmationReason,
                            confirmationEvidence = line.confirmationEvidence,
                            adjustmentSourceIdentity = line.adjustmentSourceIdentity,
                            adjustmentEvidence = line.adjustmentEvidence,
                        )
                    },
                )
            )
            replacement.requestActivation()
            return replacement
        }
    }

    data class Request(
        val settlementId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val reason: String,
        val voidedAt: Instant,
        val createReplacement: Boolean
    ) : Command<Response>

    data class Response(val settlementId: String, val status: String, val replacementSettlementId: String?)
}
