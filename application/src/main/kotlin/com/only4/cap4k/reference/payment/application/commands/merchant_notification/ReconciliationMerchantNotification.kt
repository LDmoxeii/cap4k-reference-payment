package com.only4.cap4k.reference.payment.application.commands.merchant_notification

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationRun
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationRunStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.math.BigDecimal

/**
 * A run belongs to a channel/day, not a merchant. Attribute its platform-linked items to their
 * authoritative Payment/Refund before forming one immutable notification per merchant and run.
 * Unattributed channel-only evidence cannot be sent to an arbitrary merchant.
 */
fun MerchantNotificationService.notifyCompletedReconciliationRun(
    batch: ReconciliationBatch,
    run: ReconciliationRun,
) {
    if (run.status != ReconciliationRunStatus.COMPLETED) return
    val payments = mutableMapOf<PaymentId, String>()
    val refunds = mutableMapOf<RefundId, Pair<String, PaymentId>>()
    val attributed = run.reconciliationItems.mapNotNull { item ->
        val refund = item.refundId?.let { refundId ->
            refunds.getOrPut(refundId) {
                val source = requireNotNull(Mediator.repositories.findOne(SRefund.predicateById(refundId))) {
                    "对账运行 ${run.id} 引用的退款单 $refundId 不存在"
                }
                source.merchantId to source.paymentId
            }
        }
        val paymentId = item.paymentId ?: refund?.second
        val merchantId = item.paymentId?.let { id ->
            payments.getOrPut(id) {
                requireNotNull(Mediator.repositories.findOne(SPayment.predicateById(id))) {
                    "对账运行 ${run.id} 引用的支付单 $id 不存在"
                }.merchantId
            }
        } ?: refund?.first ?: return@mapNotNull null
        require(refund == null || refund.first == merchantId) {
            "对账运行 ${run.id} 的支付和退款商户归属冲突"
        }
        AttributedItem(
            merchantId = merchantId,
            paymentId = paymentId,
            refundId = item.refundId,
            amount = item.platformAmount ?: item.channelAmount ?: BigDecimal.ZERO,
            matched = item.differenceType.name == "MATCHED",
        )
    }
    attributed.groupBy { it.merchantId }.forEach { (merchantId, items) ->
        val relatedPayments = items.mapNotNull { it.paymentId }.distinct().sortedBy { it.toString() }
        val relatedRefunds = items.mapNotNull { it.refundId }.distinct().sortedBy { it.toString() }
        createAndDeliver(
            MerchantNotificationService.Intent(
                merchantId = merchantId,
                sourceKind = "RECONCILIATION",
                sourceFactIdentity = "reconciliation-run:${run.id}:merchant:$merchantId",
                paymentId = relatedPayments.singleOrNull(),
                content = mapOf(
                    "merchantId" to merchantId,
                    "batchId" to batch.id.toString(),
                    "runId" to run.id.toString(),
                    "paymentIds" to relatedPayments.joinToString(","),
                    "refundIds" to relatedRefunds.joinToString(","),
                    "amount" to items.fold(BigDecimal.ZERO) { total, item -> total.add(item.amount) }.toPlainString(),
                    "currency" to batch.currency,
                    "status" to run.status.name,
                    "batchStatus" to batch.status.name,
                    "matchedCount" to items.count { it.matched }.toString(),
                    "differenceCount" to items.count { !it.matched }.toString(),
                ),
            ),
        )
    }
}

private data class AttributedItem(
    val merchantId: String,
    val paymentId: PaymentId?,
    val refundId: RefundId?,
    val amount: BigDecimal,
    val matched: Boolean,
)
