package com.only4.cap4k.reference.payment.application.commands.manual_review

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review.AdjudicateMerchantSettlementResultCmd
import com.only4.cap4k.reference.payment.application.commands.payment.review.AdjudicatePaymentReviewCmd
import com.only4.cap4k.reference.payment.application.commands.reconciliation.disposition.DisposeReconciliationDifferenceCmd
import com.only4.cap4k.reference.payment.application.errors.ManualReviewNotFoundException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewJson
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.manual_review_item.SManualReviewItem
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.ManualReviewItem
import com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.ManualReviewItemId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.convertRefundReservationToSuccess
import com.only4.cap4k.reference.payment.domain.aggregates.payment.releaseRefundReservation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.adjudicateManualResult
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/**
 * Resolves an authoritative ManualReviewItem by delegating to its originating aggregate.  The
 * ManualReviewItem only records the accountable, append-only history after that source decision has
 * succeeded; it is never an alternate decision model.
 */
@DesignBlockMetadata(
    tag = "command",
    name = "ResolveManualReview",
    packageName = "manual_review",
    description = "Resolve a review through its originating aggregate and append one accountable ManualReview resolution",
    aggregates = ["ManualReviewItem", "Payment", "Refund", "ReconciliationBatch", "MerchantSettlement"],
    family = "command",
)
object ResolveManualReviewCmd {
    @Service
    class Handler(
        private val manualReviewSupport: ManualReviewSupport,
        private val operationSupport: OperationSupport,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val reviewId = command.reviewId.trim().requireNotBlank("reviewId")
            val idempotencyKey = command.idempotencyKey.trim().requireNotBlank("idempotencyKey")
            val outcome = command.outcome.trim().requireNotBlank("outcome").uppercase()
            val reason = command.reason.trim().requireNotBlank("reason")
            val evidence = command.evidence.trim().requireNotBlank("evidence")
            val actorId = command.actorId.trim().requireNotBlank("actorId")
            val actorRole = command.actorRole.trim().requireNotBlank("actorRole").uppercase()
            val item = Mediator.repositories.findOne(
                SManualReviewItem.predicateById(ManualReviewItemId.parse(reviewId)),
            ) ?: throw ManualReviewNotFoundException(reviewId)
            val requestedMerchant = command.merchantId.normalized()
            item.merchantId?.let { expected ->
                requestedMerchant?.let { actual -> require(actual == expected) { "人工事项商户范围不一致" } }
            }
            val operationMerchant = item.merchantId ?: REFERENCE_MANUAL_REVIEW_SCOPE
            val requestHash = operationSupport.canonicalHash(
                reviewId,
                requestedMerchant,
                outcome,
                reason,
                evidence,
                command.remediationReference.normalized(),
                command.channelId.normalized(),
                actorId,
                actorRole,
            )
            operationSupport.replayOrNull(
                merchantId = operationMerchant,
                commandType = COMMAND_TYPE,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = requestHash,
            )?.let { operation ->
                return Response(item.id.toString(), operationSupport.receipt(operation, replay = true))
            }
            require(item.status == ManualReviewSupport.STATUS_OPEN) {
                "ManualReview $reviewId 当前状态为 ${item.status}，不能再次处置"
            }

            val resolutionIdentity = "manual-review-resolution:${item.id}:$idempotencyKey"
            resolveSource(item, command.copy(
                reviewId = reviewId,
                idempotencyKey = idempotencyKey,
                outcome = outcome,
                reason = reason,
                evidence = evidence,
                actorId = actorId,
                actorRole = actorRole,
            ), resolutionIdentity)
            manualReviewSupport.appendResolution(
                item = item,
                resolutionIdentity = resolutionIdentity,
                actorId = actorId,
                actorRole = actorRole,
                outcome = outcome,
                reason = reason,
                evidence = evidence,
                resolvedAt = command.resolvedAt,
            )
            return Response(
                reviewId = item.id.toString(),
                receipt = operationSupport.accept(
                    merchantId = operationMerchant,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = idempotencyKey,
                    canonicalRequestHash = requestHash,
                    resourceType = "ManualReviewItem",
                    resourceId = item.id.toString(),
                    resourceUrl = "/api/manual-reviews/${item.id}",
                ),
            )
        }

        private fun resolveSource(item: ManualReviewItem, command: Request, resolutionIdentity: String) {
            when (item.originKind.uppercase()) {
                "PAYMENT_REVIEW" -> resolvePaymentReview(item, command, resolutionIdentity)
                "RECONCILIATION_ITEM" -> resolveReconciliationItem(item, command)
                "REFUND_CALLBACK", "REFUND_ATTEMPT" -> resolveRefund(item, command, resolutionIdentity)
                "SETTLEMENT_CALLBACK", "SETTLEMENT_EXECUTION_ATTEMPT", "SETTLEMENT_EXECUTION" -> resolveSettlement(item, command)
                else -> throw IllegalArgumentException(
                    "人工事项 ${item.id} 的来源 ${item.originKind} 没有可复用的最终裁决；请通过其权威业务命令处理"
                )
            }
        }

        private fun resolvePaymentReview(item: ManualReviewItem, command: Request, resolutionIdentity: String) {
            val paymentId = item.relatedRef("PAYMENT")
                ?: throw IllegalArgumentException("支付复核人工事项缺少支付引用")
            Mediator.commands.send(
                AdjudicatePaymentReviewCmd.Request(
                    paymentId = PaymentId.parse(paymentId),
                    reviewId = item.originIdentity,
                    decisionIdentity = resolutionIdentity,
                    idempotencyKey = resolutionIdentity,
                    decision = command.outcome,
                    operatorIdentity = command.actorId,
                    operatorRole = command.actorRole,
                    reason = command.reason,
                    evidence = command.evidence,
                    decidedAt = command.resolvedAt,
                    // ResolveManualReview is a final, not an escalation, operation.  Keeping the
                    // original review blocked is intentionally rejected by the source command.
                    eligibilityImpact = "ALLOW_SETTLEMENT",
                    remediationReference = command.remediationReference.normalized(),
                ),
            )
        }

        private fun resolveReconciliationItem(item: ManualReviewItem, command: Request) {
            val parts = item.originIdentity.split(':', limit = 3)
            require(parts.size == 3) { "对账人工事项来源身份格式不合法" }
            val batchId = ReconciliationBatchId.parse(parts[0])
            val batch = Mediator.repositories.findOne(SReconciliationBatch.predicateById(batchId))
                ?: throw IllegalArgumentException("对账批次 ${parts[0]} 不存在")
            val run = batch.reconciliationRuns.firstOrNull { it.id.toString() == parts[1] }
                ?: throw IllegalArgumentException("人工事项引用的对账运行 ${parts[1]} 不存在")
            val difference = run.reconciliationItems.firstOrNull { it.differenceIdentity == parts[2] }
                ?: throw IllegalArgumentException("人工事项引用的对账差异 ${parts[2]} 不存在")
            require(command.outcome != "ESCALATE") { "ESCALATE 不会解除人工事项，不能通过 ResolveManualReview 提交" }
            Mediator.commands.send(
                DisposeReconciliationDifferenceCmd.Request(
                    reconciliationBatchId = batchId,
                    itemId = difference.id.toString(),
                    merchantId = command.merchantId.normalized(),
                    channelId = command.channelId.normalized(),
                    operatorIdentity = command.actorId,
                    idempotencyKey = "${command.idempotencyKey}:reconciliation",
                    operatorRole = command.actorRole,
                    conclusion = command.outcome,
                    settlementImpact = if (command.outcome == "CONFIRM_PLATFORM_FACT") {
                        "CONFIRMS_SETTLEMENT_FACT"
                    } else {
                        "DOES_NOT_BLOCK_SETTLEMENT"
                    },
                    evidence = command.evidence,
                    reason = command.reason,
                    followUp = command.reason,
                    disposedAt = command.resolvedAt,
                ),
            )
        }

        private fun resolveRefund(item: ManualReviewItem, command: Request, resolutionIdentity: String) {
            require(command.outcome in REFUND_OUTCOMES) {
                "退款人工处置结果必须为 CONFIRM_SUCCESS 或 CONFIRM_FAILURE"
            }
            val refundId = item.relatedRef("REFUND")
                ?: item.originIdentity.substringBefore(':').takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("退款人工事项缺少退款引用")
            val attemptId = item.relatedRef("REFUND_ATTEMPT")
                ?: item.originIdentity.split(':').getOrNull(1)?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("退款人工事项缺少退款尝试引用")
            val refund = Mediator.repositories.findOne(SRefund.predicateById(RefundId.parse(refundId)))
                ?: throw RefundNotFoundException(RefundId.parse(refundId))
            item.merchantId?.let { require(it == refund.merchantId) { "人工事项退款商户归属不一致" } }
            command.merchantId.normalized()?.let { require(it == refund.merchantId) { "退款商户范围不一致" } }
            val payment = Mediator.repositories.findOne(SPayment.predicateById(refund.paymentId))
                ?: throw PaymentNotFoundException(refund.paymentId)
            val outcome = refund.adjudicateManualResult(
                attemptId = com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttemptId.parse(attemptId),
                resolutionIdentity = resolutionIdentity,
                operatorIdentity = command.actorId,
                operatorRole = command.actorRole,
                outcome = command.outcome,
                reason = command.reason,
                evidence = command.evidence,
                adjudicatedAt = LocalDateTime.ofInstant(command.resolvedAt, ZoneOffset.UTC),
            )
            if (outcome.reservationReleasedNow) payment.releaseRefundReservation(refund.amount)
            if (outcome.reservationConvertedToSuccessNow) payment.convertRefundReservationToSuccess(refund.amount)
        }

        private fun resolveSettlement(item: ManualReviewItem, command: Request) {
            val settlementId = item.relatedRef("MERCHANT_SETTLEMENT")
                ?: item.originIdentity.substringBefore(':').takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("结算人工事项缺少结算引用")
            val executionId = item.relatedRef("SETTLEMENT_EXECUTION")
                ?: item.relatedRef("SETTLEMENT_EXECUTION_ATTEMPT")
                ?: item.originIdentity.split(':').getOrNull(1)?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("结算人工事项缺少执行尝试引用")
            val finalResult = when (command.outcome) {
                "CONFIRM_SUCCESS", "SUCCESS" -> "SUCCESS"
                "CONFIRM_FAILURE", "FAILED", "FAILURE" -> "FAILED"
                else -> throw IllegalArgumentException("结算人工处置结果必须为 CONFIRM_SUCCESS 或 CONFIRM_FAILURE")
            }
            Mediator.commands.send(
                AdjudicateMerchantSettlementResultCmd.Request(
                    merchantSettlementId = MerchantSettlementId.parse(settlementId),
                    executionId = executionId,
                    operatorIdentity = command.actorId,
                    operatorRole = command.actorRole,
                    finalResult = finalResult,
                    adjudicatedAt = command.resolvedAt,
                    evidence = "${command.reason}；${command.evidence}",
                ),
            )
        }

        private fun ManualReviewItem.relatedRef(resourceType: String): String? =
            ManualReviewJson.decodeRefs(relatedRefsJson)
                .firstOrNull { it.resourceType == resourceType }
                ?.resourceId
    }

    data class Request(
        val reviewId: String,
        val idempotencyKey: String,
        val merchantId: String?,
        val outcome: String,
        val reason: String,
        val evidence: String,
        val remediationReference: String?,
        val channelId: String?,
        /** Only the trusted HTTP/fixture adapter may populate these values. */
        val actorId: String,
        val actorRole: String,
        /** The trusted adapter supplies the server logical-clock time. */
        val resolvedAt: Instant,
    ) : Command<Response>

    data class Response(
        val reviewId: String,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "ResolveManualReview"
    private const val REFERENCE_MANUAL_REVIEW_SCOPE = "reference-manual-review"
    private val REFUND_OUTCOMES = setOf("CONFIRM_SUCCESS", "CONFIRM_FAILURE")

    private fun String.requireNotBlank(field: String): String = also {
        require(it.isNotBlank()) { "$field 不能为空" }
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
