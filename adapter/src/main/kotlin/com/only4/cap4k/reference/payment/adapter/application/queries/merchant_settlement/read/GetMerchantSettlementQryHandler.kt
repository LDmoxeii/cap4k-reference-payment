package com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.application.queries.merchant_settlement.read.GetMerchantSettlementQry
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "GetMerchantSettlement",
    packageName = "merchant_settlement.read",
    description = "Read the full merchant settlement scope, lines, fee evidence, execution attempts, receipts, conflicts, and replacement trail",
    aggregates = ["MerchantSettlement"],
    family = "query-handler"
)
class GetMerchantSettlementQryHandler : QueryHandler<GetMerchantSettlementQry.Request, GetMerchantSettlementQry.Response> {

    override fun handle(query: GetMerchantSettlementQry.Request): GetMerchantSettlementQry.Response {
        val settlement = Mediator.repositories.findOne(
            SMerchantSettlement.predicateById(MerchantSettlementId.parse(query.settlementId))
        ) ?: throw MerchantSettlementNotFoundException(query.settlementId)

        return GetMerchantSettlementQry.Response(
            settlementId = settlement.id.toString(),
            merchantId = settlement.merchantId,
            channelId = settlement.channelId,
            currency = settlement.currency,
            periodType = settlement.periodType,
            periodStart = settlement.periodStart.toInstant(ZoneOffset.UTC),
            periodEnd = settlement.periodEnd.toInstant(ZoneOffset.UTC),
            businessTimezone = settlement.businessTimezone,
            scopeIdentity = settlement.scopeIdentity,
            effectiveScopeIdentity = settlement.effectiveScopeIdentity,
            status = settlement.status.name,
            eligibleCount = settlement.eligibleCount,
            excludedCount = settlement.excludedCount,
            blockerSummary = settlement.blockerSummary,
            paymentGrossAmount = settlement.paymentGrossAmount,
            refundGrossAmount = settlement.refundGrossAmount,
            feeTotalAmount = settlement.feeTotalAmount,
            adjustmentTotalAmount = settlement.adjustmentTotalAmount,
            netAmount = settlement.netAmount,
            compositionFrozen = settlement.compositionFrozen,
            executionGroupIdentity = settlement.executionGroupIdentity,
            predecessorSettlementId = settlement.predecessorSettlementId,
            replacementSettlementId = settlement.replacementSettlementId,
            confirmedBy = settlement.confirmedBy,
            confirmedAt = settlement.confirmedAt?.toInstant(ZoneOffset.UTC),
            voidedBy = settlement.voidedBy,
            voidReason = settlement.voidReason,
            voidedAt = settlement.voidedAt?.toInstant(ZoneOffset.UTC),
            settledFactFormed = settlement.settledFactFormed,
            externalSettlementIdentity = settlement.externalSettlementIdentity,
            completedAt = settlement.completedAt?.toInstant(ZoneOffset.UTC),
            lastRejectionSummary = settlement.lastRejectionSummary,
            lastConflictSummary = settlement.lastConflictSummary,
            lastReviewSummary = settlement.lastReviewSummary,
            lines = settlement.settlementLines.map { line ->
                GetMerchantSettlementQry.Response.SettlementLineSummary(
                    lineId = line.id.toString(),
                    lineIdentity = line.lineIdentity,
                    sourceKind = line.sourceKind.name,
                    transactionKind = line.transactionKind.name,
                    sourceFactIdentity = line.sourceFactIdentity,
                    feeFactIdentity = line.feeFactIdentity,
                    paymentId = line.paymentId?.toString(),
                    paymentAttemptId = line.paymentAttemptId,
                    refundId = line.refundId?.toString(),
                    refundAttemptId = line.refundAttemptId,
                    reconciliationBatchId = line.reconciliationBatchId?.toString(),
                    reconciliationRunId = line.reconciliationRunId,
                    reconciliationItemId = line.reconciliationItemId,
                    reconciliationConfirmationFactId = line.reconciliationConfirmationFactId,
                    externalTransactionIdentity = line.externalTransactionIdentity,
                    grossAmount = line.grossAmount,
                    feeAmount = line.feeAmount,
                    signedNetAmount = line.signedNetAmount,
                    currency = line.currency,
                    occurredAt = line.occurredAt.toInstant(ZoneOffset.UTC),
                    recordedAt = line.recordedAt.toInstant(ZoneOffset.UTC),
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
            attempts = settlement.settlementExecutionAttempts.map { attempt ->
                GetMerchantSettlementQry.Response.SettlementExecutionAttemptSummary(
                    attemptId = attempt.id.toString(),
                    attemptSequence = attempt.attemptSequence,
                    executionGroupIdentity = attempt.executionGroupIdentity,
                    requestIdentity = attempt.requestIdentity,
                    channelId = attempt.channelId,
                    status = attempt.status.name,
                    initiatedAt = attempt.initiatedAt.toInstant(ZoneOffset.UTC),
                    acceptedAt = attempt.acceptedAt?.toInstant(ZoneOffset.UTC),
                    reviewAfterMinutesSnapshot = attempt.reviewAfterMinutesSnapshot,
                    reviewAfterAt = attempt.reviewAfterAt.toInstant(ZoneOffset.UTC),
                    amount = attempt.amount,
                    currency = attempt.currency,
                    externalSettlementIdentity = attempt.externalSettlementIdentity,
                    finalResult = attempt.finalResult?.name,
                    resultOccurredAt = attempt.resultOccurredAt?.toInstant(ZoneOffset.UTC),
                    notificationReceiveCount = attempt.notificationReceiveCount,
                    rejectionSummary = attempt.rejectionSummary,
                    conflictSummary = attempt.conflictSummary,
                    receipts = attempt.settlementResultReceipts.map { receipt ->
                        GetMerchantSettlementQry.Response.SettlementResultReceiptSummary(
                            receiptId = receipt.id.toString(),
                            notificationIdentity = receipt.notificationIdentity,
                            payloadFingerprint = receipt.payloadFingerprint,
                            channelId = receipt.channelId,
                            executionGroupIdentity = receipt.executionGroupIdentity,
                            requestIdentity = receipt.requestIdentity,
                            externalSettlementIdentity = receipt.externalSettlementIdentity,
                            amount = receipt.amount,
                            currency = receipt.currency,
                            result = receipt.result,
                            resultCode = receipt.resultCode,
                            occurredAt = receipt.occurredAt.toInstant(ZoneOffset.UTC),
                            firstReceivedAt = receipt.firstReceivedAt.toInstant(ZoneOffset.UTC),
                            lastReceivedAt = receipt.lastReceivedAt.toInstant(ZoneOffset.UTC),
                            receiveCount = receipt.receiveCount,
                            verified = receipt.verified,
                            accepted = receipt.accepted,
                            decision = receipt.decision.name,
                            verdictSummary = receipt.verdictSummary,
                            rejectionSummary = receipt.rejectionSummary,
                            conflictSummary = receipt.conflictSummary,
                        )
                    },
                )
            },
        )
    }
}
