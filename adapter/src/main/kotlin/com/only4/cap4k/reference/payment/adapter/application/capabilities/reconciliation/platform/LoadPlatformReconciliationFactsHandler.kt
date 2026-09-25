package com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.platform

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.platform.LoadPlatformReconciliationFacts
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.currentReviewEligibility
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
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
            "select p from Payment p where p.status in :statuses and p.currency = :currency",
            Payment::class.java,
        )
            .setParameter("statuses", listOf(PaymentStatus.SUCCEEDED, PaymentStatus.RESULT_UNKNOWN))
            .setParameter("currency", currency)
            .resultList
            .flatMap { payment ->
                val eligibility = payment.currentReviewEligibility()
                val attempts = when (payment.status) {
                    PaymentStatus.SUCCEEDED -> {
                        val channelIdentity = payment.channelTransactionId?.takeIf { it.isNotBlank() }
                            ?: return@flatMap emptyList()
                        listOfNotNull(payment.attempts.lastOrNull {
                            it.channelId == request.channelId && it.channelTransactionId == channelIdentity
                        })
                    }

                    PaymentStatus.RESULT_UNKNOWN -> payment.attempts.filter {
                        it.channelId == request.channelId &&
                            it.status == PaymentAttemptStatus.RESULT_UNKNOWN &&
                            !it.channelTransactionId.isNullOrBlank() &&
                            it.resultOccurredAt != null
                    }

                    else -> emptyList()
                }
                attempts.mapNotNull { attempt ->
                    val occurredAt = when (payment.status) {
                        PaymentStatus.SUCCEEDED -> payment.succeededAt
                        PaymentStatus.RESULT_UNKNOWN -> attempt.resultOccurredAt
                        else -> null
                    } ?: return@mapNotNull null
                    if (occurredAt.toBusinessDate(zone) != request.reconciliationDate) return@mapNotNull null
                    val channelIdentity = attempt.channelTransactionId?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    PlatformReconciliationFact(
                        factIdentity = if (payment.status == PaymentStatus.SUCCEEDED) {
                            "PAYMENT:${payment.id}"
                        } else {
                            "PAYMENT:${payment.id}:ATTEMPT:${attempt.id}"
                        },
                        transactionKind = ReconciliationTransactionKind.PAYMENT,
                        paymentId = payment.id,
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
                        settlementEligible = payment.status == PaymentStatus.SUCCEEDED && eligibility.settlementEligible,
                    )
                }
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
                    paymentId = refund.paymentId,
                    paymentAttemptId = null,
                    refundId = refund.id,
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
