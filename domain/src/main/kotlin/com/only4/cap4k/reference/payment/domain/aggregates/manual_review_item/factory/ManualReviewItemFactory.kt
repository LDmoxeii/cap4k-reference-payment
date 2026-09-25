package com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.ManualReviewItem
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "ManualReviewItem",
    name = "ManualReviewItemFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.factory",
    description = "",
    type = "factory",
    root = false,
)
class ManualReviewItemFactory : AggregateFactory<ManualReviewItemFactory.Payload, ManualReviewItem> {
    override fun create(entityPayload: Payload): ManualReviewItem = ManualReviewItem(
        reviewIdentity = entityPayload.reviewIdentity,
        type = entityPayload.type,
        status = entityPayload.status,
        finality = entityPayload.finality,
        merchantId = entityPayload.merchantId,
        paymentId = entityPayload.paymentId?.let(com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId::parse),
        originKind = entityPayload.originKind,
        originIdentity = entityPayload.originIdentity,
        summary = entityPayload.summary,
        relatedRefsJson = entityPayload.relatedRefsJson,
        blockingScopesJson = entityPayload.blockingScopesJson,
        evidenceRefsJson = entityPayload.evidenceRefsJson,
        sortTime = entityPayload.sortTime,
        resolvedAt = entityPayload.resolvedAt,
    )

    data class Payload(
        val reviewIdentity: String,
        val type: String,
        val status: String,
        val finality: String,
        val merchantId: String?,
        val paymentId: String?,
        val originKind: String,
        val originIdentity: String,
        val summary: String,
        val relatedRefsJson: String,
        val blockingScopesJson: String,
        val evidenceRefsJson: String,
        val sortTime: LocalDateTime,
        val resolvedAt: LocalDateTime? = null,
    ) : AggregatePayload<ManualReviewItem>
}
