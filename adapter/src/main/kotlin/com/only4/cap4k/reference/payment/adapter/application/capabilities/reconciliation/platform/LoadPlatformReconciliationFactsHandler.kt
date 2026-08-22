package com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.platform

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.platform.LoadPlatformReconciliationFacts
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.currentReviewEligibility
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.PlatformReconciliationFact
import com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "LoadPlatformReconciliationFacts",
    packageName = "reconciliation.platform",
    description = "Project immutable payment and refund facts for a reconciliation scope without returning aggregate write models",
    aggregates = ["ReconciliationBatch"],
    family = "capability-handler"
)
class LoadPlatformReconciliationFactsHandler(
    private val entityManager: EntityManager,
) : CapabilityHandler<LoadPlatformReconciliationFacts.Request, LoadPlatformReconciliationFacts.Response> {

    override fun call(request: LoadPlatformReconciliationFacts.Request): LoadPlatformReconciliationFacts.Response {
        val zone = ZoneId.of(request.businessTimezone)
        val currency = request.currency.uppercase()
        val payments = entityManager.createQuery(
            "select p from Payment p where p.status = :status and p.currency = :currency",
            Payment::class.java,
        )
            .setParameter("status", PaymentStatus.SUCCEEDED)
            .setParameter("currency", currency)
            .resultList
            .mapNotNull { payment ->
                val occurredAt = payment.succeededAt ?: return@mapNotNull null
                if (occurredAt.toBusinessDate(zone) != request.reconciliationDate) return@mapNotNull null
                val channelIdentity = payment.channelTransactionId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val attempt = payment.attempts.lastOrNull {
                    it.channelId == request.channelId && it.channelTransactionId == channelIdentity
                } ?: return@mapNotNull null
                val eligibility = payment.currentReviewEligibility()
                PlatformReconciliationFact(
                    factIdentity = "PAYMENT:${payment.id}",
                    transactionKind = ReconciliationTransactionKind.PAYMENT,
                    paymentId = payment.id.toString(),
                    paymentAttemptId = attempt.id.toString(),
                    refundId = null,
                    refundAttemptId = null,
                    channelTransactionIdentity = channelIdentity,
                    amount = payment.amount,
                    currency = payment.currency,
                    rawStatus = payment.status.name,
                    occurredAt = occurredAt.toInstant(ZoneOffset.UTC),
                    recordedAt = (payment.updatedAt ?: occurredAt).toInstant(ZoneOffset.UTC),
                    paymentReviewIdentitySnapshot = eligibility.blockingReviewIdentities.joinToString(",").ifBlank { null },
                    paymentReviewSummary = eligibility.blockingReviewSummaries.joinToString(" | ").ifBlank { null },
                    settlementEligible = eligibility.settlementEligible,
                )
            }
        val refunds = entityManager.createQuery(
            "select r from Refund r where r.status in :statuses and r.currency = :currency and r.channelId = :channelId",
            Refund::class.java,
        )
            .setParameter("statuses", listOf(RefundStatus.SUCCEEDED, RefundStatus.RESULT_UNKNOWN))
            .setParameter("currency", currency)
            .setParameter("channelId", request.channelId)
            .resultList
            .mapNotNull { refund ->
                val channelIdentity = refund.channelRefundId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val attempt = refund.attempts.lastOrNull {
                    it.channelId == request.channelId && it.channelRefundId == channelIdentity
                } ?: return@mapNotNull null
                val occurredAt = refund.finalizedAt ?: attempt.resultOccurredAt ?: return@mapNotNull null
                if (occurredAt.toBusinessDate(zone) != request.reconciliationDate) return@mapNotNull null
                PlatformReconciliationFact(
                    factIdentity = "REFUND:${refund.id}",
                    transactionKind = ReconciliationTransactionKind.REFUND,
                    paymentId = refund.paymentId.toString(),
                    paymentAttemptId = null,
                    refundId = refund.id.toString(),
                    refundAttemptId = attempt.id.toString(),
                    channelTransactionIdentity = channelIdentity,
                    amount = refund.amount,
                    currency = refund.currency,
                    rawStatus = refund.status.name,
                    occurredAt = occurredAt.toInstant(ZoneOffset.UTC),
                    recordedAt = (refund.updatedAt ?: occurredAt).toInstant(ZoneOffset.UTC),
                )
            }
        return LoadPlatformReconciliationFacts.Response(
            facts = (payments + refunds).sortedWith(
                compareBy<PlatformReconciliationFact>({ it.occurredAt }, { it.factIdentity })
            )
        )
    }

    private fun LocalDateTime.toBusinessDate(zone: ZoneId) =
        toInstant(ZoneOffset.UTC).atZone(zone).toLocalDate()
}
