package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toContractMoney
import com.only4.cap4k.reference.payment.application.queries.merchant_settlement.read.GetMerchantSettlementQry
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read.MerchantSettlementContractStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.GetMerchantSettlementEndpoint
import org.springframework.stereotype.Component

@Component
class GetMerchantSettlementEndpointHandler : EndpointHandler<GetMerchantSettlementEndpoint.Request, GetMerchantSettlementEndpoint.Response> {
    override fun handle(request: GetMerchantSettlementEndpoint.Request): GetMerchantSettlementEndpoint.Response {
        val response = Mediator.queries.ask(GetMerchantSettlementQry.Request(MerchantSettlementId.parse(request.settlementId)))
        val aggregateStatus = MerchantSettlementStatus.valueOf(response.status)
        return GetMerchantSettlementEndpoint.Response(
            settlementId = response.merchantSettlementId.toString(),
            merchantId = response.merchantId,
            executionChannelId = response.executionChannelId,
            currency = response.currency,
            periodType = response.periodType,
            periodStart = response.periodStart,
            periodEnd = response.periodEnd,
            businessTimezone = response.businessTimezone,
            scopeIdentity = response.scopeIdentity,
            effectiveScopeIdentity = response.effectiveScopeIdentity,
            status = MerchantSettlementContractStatus.publicStatus(aggregateStatus),
            finality = MerchantSettlementContractStatus.finality(aggregateStatus),
            eligibleCount = response.eligibleCount,
            excludedCount = response.excludedCount,
            blockerSummary = response.blockerSummary,
            grossMoney = response.paymentGrossAmount.toContractMoney(response.currency),
            refundMoney = response.refundGrossAmount.toContractMoney(response.currency),
            feeMoney = response.feeTotalAmount.toContractMoney(response.currency),
            adjustmentMoney = response.adjustmentTotalAmount.toContractMoney(response.currency),
            netMoney = response.netAmount.toContractMoney(response.currency),
            compositionFrozen = response.compositionFrozen,
            executionGroupIdentity = response.executionGroupIdentity,
            predecessorSettlementId = response.predecessorSettlementId?.toString(),
            replacementSettlementId = response.replacementSettlementId?.toString(),
            confirmedBy = response.confirmedBy,
            confirmedAt = response.confirmedAt,
            confirmedReason = response.confirmedReason,
            confirmedEvidence = response.confirmedEvidence,
            voidedBy = response.voidedBy,
            voidReason = response.voidReason,
            voidEvidence = response.voidEvidence,
            voidedAt = response.voidedAt,
            settledFactFormed = response.settledFactFormed,
            externalSettlementIdentity = response.externalSettlementIdentity,
            completedAt = response.completedAt,
            lastRejectionSummary = response.lastRejectionSummary,
            lastConflictSummary = response.lastConflictSummary,
            lastReviewSummary = response.lastReviewSummary,
            lines = response.lines.map { line ->
                GetMerchantSettlementEndpoint.Response.SettlementLineSummary(
                    lineId = line.lineId,
                    lineIdentity = line.lineIdentity,
                    sourceKind = line.sourceKind,
                    transactionKind = line.transactionKind,
                    sourceFactIdentity = line.sourceFactIdentity,
                    decision = line.decision,
                    reasonCode = line.reasonCode,
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
                    grossMoney = line.grossAmount.toContractMoney(line.currency),
                    feeMoney = line.feeAmount.toContractMoney(line.currency),
                    signedNetMoney = line.signedNetAmount.toContractMoney(line.currency),
                    occurredAt = line.occurredAt,
                    recordedAt = line.recordedAt,
                    feeBasisPoints = line.feeBasisPoints,
                    feeFixedMoney = line.feeFixedAmount?.toContractMoney(line.currency),
                    feeRoundingMode = line.feeRoundingMode,
                    feeCurrencyPrecision = line.feeCurrencyPrecision,
                    feeCalculationMoney = line.feeCalculationAmount?.toContractMoney(line.currency),
                    eligibilityBasis = line.eligibilityBasis,
                    confirmationReason = line.confirmationReason,
                    confirmationEvidence = line.confirmationEvidence,
                    adjustmentSourceIdentity = line.adjustmentSourceIdentity,
                    adjustmentEvidence = line.adjustmentEvidence,
                )
            },
            attempts = response.attempts.map { attempt ->
                GetMerchantSettlementEndpoint.Response.SettlementExecutionAttemptSummary(
                    executionId = attempt.executionId,
                    idempotencyKey = attempt.idempotencyKey,
                    executorScript = attempt.executorScript,
                    executorObservation = attempt.executorObservation,
                    diagnosticSummary = attempt.diagnosticSummary,
                    attemptId = attempt.attemptId,
                    attemptSequence = attempt.attemptSequence,
                    executionGroupIdentity = attempt.executionGroupIdentity,
                    requestIdentity = attempt.requestIdentity,
                    channelId = attempt.channelId,
                    status = MerchantSettlementContractStatus.publicExecutionStatus(
                        SettlementExecutionAttemptStatus.valueOf(attempt.status)
                    ),
                    initiatedAt = attempt.initiatedAt,
                    acceptedAt = attempt.acceptedAt,
                    reviewAfterMinutesSnapshot = attempt.reviewAfterMinutesSnapshot,
                    reviewAfterAt = attempt.reviewAfterAt,
                    money = attempt.amount.toContractMoney(attempt.currency),
                    externalSettlementIdentity = attempt.externalSettlementIdentity,
                    finalResult = attempt.finalResult,
                    resultOccurredAt = attempt.resultOccurredAt,
                    notificationReceiveCount = attempt.notificationReceiveCount,
                    rejectionSummary = attempt.rejectionSummary,
                    conflictSummary = attempt.conflictSummary,
                    receipts = attempt.receipts.map { receipt ->
                        GetMerchantSettlementEndpoint.Response.SettlementResultReceiptSummary(
                            executionId = attempt.executionId,
                            receiptId = receipt.receiptId,
                            notificationIdentity = receipt.notificationIdentity,
                            payloadFingerprint = receipt.payloadFingerprint,
                            channelId = receipt.channelId,
                            executionGroupIdentity = receipt.executionGroupIdentity,
                            requestIdentity = receipt.requestIdentity,
                            externalSettlementIdentity = receipt.externalSettlementIdentity,
                            money = receipt.amount.toContractMoney(receipt.currency),
                            result = receipt.result,
                            resultCode = receipt.resultCode,
                            occurredAt = receipt.occurredAt,
                            firstReceivedAt = receipt.firstReceivedAt,
                            lastReceivedAt = receipt.lastReceivedAt,
                            receiveCount = receipt.receiveCount,
                            verified = receipt.verified,
                            accepted = receipt.accepted,
                            decision = receipt.decision,
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
