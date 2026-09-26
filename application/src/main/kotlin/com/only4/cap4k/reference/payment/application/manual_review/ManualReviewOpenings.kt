package com.only4.cap4k.reference.payment.application.manual_review

import com.only4.cap4k.reference.payment.contract.common.ManualReviewBlockingScope
import com.only4.cap4k.reference.payment.contract.common.ManualReviewEvidenceRef
import com.only4.cap4k.reference.payment.contract.common.ManualReviewReference
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementExecutionAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationItem
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationRun
import com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttempt
import java.time.ZoneOffset

/**
 * Bridges an already-recorded domain fact to the single authoritative ManualReviewItem resource.
 *
 * These functions deliberately do not infer or apply a business decision.  They only keep the
 * originating aggregate's identity, evidence and blocked scope discoverable in the review list.
 * Each identity is derived solely from the source fact so replaying the same command reuses the
 * existing ManualReviewItem through [ManualReviewSupport.open].
 */
fun ManualReviewSupport.openPaymentReview(payment: Payment, paymentReviewIdentity: String) {
    val review = payment.reviewCases.firstOrNull { it.reviewIdentity == paymentReviewIdentity } ?: return
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review:payment:${review.reviewIdentity}",
            type = "PAYMENT_${review.type.name}",
            merchantId = payment.merchantId,
            originKind = "PAYMENT_REVIEW",
            originIdentity = review.reviewIdentity,
            summary = review.summary,
            relatedRefs = buildList {
                add(ref("PAYMENT", payment.id.toString()))
                add(ref("PAYMENT_REVIEW", review.reviewIdentity))
                review.triggeringAttemptIdentities.csvValues().forEach { add(ref("PAYMENT_ATTEMPT", it)) }
            },
            blockingScopes = listOf(
                scope("PAYMENT", payment.id.toString()),
                scope("MERCHANT_ORDER", "${payment.merchantId}:${payment.merchantOrderNumber}"),
            ),
            evidenceRefs = buildList {
                add(evidence("PAYMENT_REVIEW", review.reviewIdentity, review.summary))
                review.triggeringReceiptIdentities.csvValues().forEach {
                    add(evidence("PAYMENT_NOTIFICATION_RECEIPT", it))
                }
            },
            sortTime = review.openedAt.toInstant(ZoneOffset.UTC),
        ),
    )
}

fun ManualReviewSupport.openRefundResultReview(
    refund: Refund,
    refundAttemptId: String,
    notificationId: String,
    type: String,
    summary: String,
) {
    val identity = "manual-review:refund-result:${type.uppercase()}:${refund.id}:$refundAttemptId:$notificationId"
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = identity,
            type = type,
            merchantId = refund.merchantId,
            originKind = "REFUND_CALLBACK",
            originIdentity = "${refund.id}:$refundAttemptId:$notificationId",
            summary = summary,
            relatedRefs = listOf(
                ref("REFUND", refund.id.toString()),
                ref("PAYMENT", refund.paymentId.toString()),
                ref("REFUND_ATTEMPT", refundAttemptId),
                ref("REFUND_NOTIFICATION", notificationId),
            ),
            blockingScopes = listOf(
                scope("PAYMENT", refund.paymentId.toString()),
                scope("REFUND", refund.id.toString()),
            ),
            evidenceRefs = listOf(
                evidence("REFUND_NOTIFICATION", notificationId, summary),
                evidence("REFUND_ATTEMPT", refundAttemptId),
            ),
        ),
    )
}

fun ManualReviewSupport.openRefundThresholdReview(refund: Refund, attempt: RefundAttempt) {
    val attemptId = attempt.id.toString()
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review:refund-overdue:${refund.id}:$attemptId",
            type = "REFUND_RESULT_OVERDUE",
            merchantId = refund.merchantId,
            originKind = "REFUND_ATTEMPT",
            originIdentity = "${refund.id}:$attemptId",
            summary = "退款尝试 $attemptId 超过冻结的结果复核阈值，退款预算继续占用",
            relatedRefs = listOf(
                ref("REFUND", refund.id.toString()),
                ref("PAYMENT", refund.paymentId.toString()),
                ref("REFUND_ATTEMPT", attemptId),
            ),
            blockingScopes = listOf(
                scope("PAYMENT", refund.paymentId.toString()),
                scope("REFUND", refund.id.toString()),
            ),
            evidenceRefs = listOf(
                evidence("REFUND_ATTEMPT", attemptId, attempt.rejectionSummary),
            ),
            sortTime = attempt.reviewAfterAt.toInstant(ZoneOffset.UTC),
        ),
    )
}

fun ManualReviewSupport.openReconciliationDifferenceReview(
    batch: ReconciliationBatch,
    run: ReconciliationRun,
    item: ReconciliationItem,
) {
    val batchId = batch.id.toString()
    val runId = run.id.toString()
    val differenceId = item.differenceIdentity
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review:reconciliation-difference:$batchId:$runId:$differenceId",
            type = "RECONCILIATION_${item.differenceType.name}",
            merchantId = null,
            originKind = "RECONCILIATION_ITEM",
            originIdentity = "$batchId:$runId:$differenceId",
            summary = "对账运行 $runId 存在阻断结算的差异 $differenceId（${item.differenceType.name}）",
            relatedRefs = buildList {
                add(ref("RECONCILIATION_BATCH", batchId))
                add(ref("RECONCILIATION_RUN", runId))
                add(ref("RECONCILIATION_ITEM", differenceId))
                item.paymentId?.let { add(ref("PAYMENT", it.toString())) }
                item.refundId?.let { add(ref("REFUND", it.toString())) }
            },
            blockingScopes = listOf(
                scope("RECONCILIATION_BATCH", batchId),
                scope("RECONCILIATION_RUN", runId),
            ),
            evidenceRefs = buildList {
                item.channelRecordIdentity?.let { add(evidence("CHANNEL_STATEMENT_RECORD", it)) }
                item.platformFactIdentity?.let { add(evidence("PLATFORM_RECONCILIATION_FACT", it)) }
                item.channelTransactionIdentity?.let { add(evidence("CHANNEL_TRANSACTION", it)) }
            }.ifEmpty { listOf(evidence("RECONCILIATION_ITEM", differenceId)) },
            sortTime = (run.completedAt ?: run.startedAt).toInstant(ZoneOffset.UTC),
        ),
    )
}

fun ManualReviewSupport.openStatementFetchFailureReview(batch: ReconciliationBatch) {
    val batchId = batch.id.toString()
    val reason = batch.blockingReason ?: "渠道账单或平台资金事实暂时不可用"
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review:statement-fetch:$batchId",
            type = "RECONCILIATION_STATEMENT_FETCH_FAILED",
            merchantId = null,
            originKind = "RECONCILIATION_BATCH",
            originIdentity = batchId,
            summary = reason,
            relatedRefs = listOf(ref("RECONCILIATION_BATCH", batchId)),
            blockingScopes = listOf(scope("RECONCILIATION_BATCH", batchId)),
            evidenceRefs = listOf(evidence("STATEMENT_FETCH_FAILURE", batchId, reason)),
        ),
    )
}

fun ManualReviewSupport.openNegativeSettlementReview(settlement: MerchantSettlement) {
    val settlementId = settlement.id.toString()
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review:settlement-negative-net:$settlementId",
            type = "SETTLEMENT_NEGATIVE_NET",
            merchantId = settlement.merchantId,
            originKind = "MERCHANT_SETTLEMENT",
            originIdentity = settlementId,
            summary = "结算单 $settlementId 的净额为 ${settlement.netAmount}，需要人工复核",
            relatedRefs = listOf(ref("MERCHANT_SETTLEMENT", settlementId)),
            blockingScopes = listOf(
                scope("SETTLEMENT_SCOPE", settlement.scopeIdentity),
                scope("MERCHANT_SETTLEMENT", settlementId),
            ),
            evidenceRefs = listOf(evidence("SETTLEMENT_NET_AMOUNT", settlementId, settlement.netAmount.toPlainString())),
        ),
    )
}

fun ManualReviewSupport.openSettlementResultReview(
    settlement: MerchantSettlement,
    executionId: String,
    notificationId: String,
    type: String,
    summary: String,
) {
    val settlementId = settlement.id.toString()
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review:settlement-result:${type.uppercase()}:$settlementId:$executionId:$notificationId",
            type = type,
            merchantId = settlement.merchantId,
            originKind = "SETTLEMENT_CALLBACK",
            originIdentity = "$settlementId:$executionId:$notificationId",
            summary = summary,
            relatedRefs = listOf(
                ref("MERCHANT_SETTLEMENT", settlementId),
                ref("SETTLEMENT_EXECUTION", executionId),
                ref("SETTLEMENT_NOTIFICATION", notificationId),
            ),
            blockingScopes = listOf(
                scope("SETTLEMENT_SCOPE", settlement.scopeIdentity),
                scope("MERCHANT_SETTLEMENT", settlementId),
            ),
            evidenceRefs = listOf(
                evidence("SETTLEMENT_NOTIFICATION", notificationId, summary),
                evidence("SETTLEMENT_EXECUTION", executionId),
            ),
        ),
    )
}

fun ManualReviewSupport.openSettlementUnknownThresholdReview(
    settlement: MerchantSettlement,
    attempt: SettlementExecutionAttempt,
) {
    val settlementId = settlement.id.toString()
    val executionId = attempt.executionId
    open(
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review:settlement-unknown-overdue:$settlementId:$executionId",
            type = "SETTLEMENT_RESULT_UNKNOWN_OVERDUE",
            merchantId = settlement.merchantId,
            originKind = "SETTLEMENT_EXECUTION",
            originIdentity = "$settlementId:$executionId",
            summary = settlement.lastReviewSummary ?: "结算执行 $executionId 的结果未知且超过复核阈值",
            relatedRefs = listOf(
                ref("MERCHANT_SETTLEMENT", settlementId),
                ref("SETTLEMENT_EXECUTION", executionId),
            ),
            blockingScopes = listOf(
                scope("SETTLEMENT_SCOPE", settlement.scopeIdentity),
                scope("MERCHANT_SETTLEMENT", settlementId),
            ),
            evidenceRefs = listOf(evidence("SETTLEMENT_EXECUTION", executionId, attempt.verdictSummary)),
            sortTime = attempt.reviewAfterAt.toInstant(ZoneOffset.UTC),
        ),
    )
}

private fun ref(resourceType: String, resourceId: String) = ManualReviewReference(resourceType, resourceId)
private fun scope(scopeType: String, scopeId: String) = ManualReviewBlockingScope(scopeType, scopeId)
private fun evidence(evidenceType: String, evidenceId: String, summary: String? = null) =
    ManualReviewEvidenceRef(evidenceType, evidenceId, summary)

private fun String?.csvValues(): List<String> = this?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
