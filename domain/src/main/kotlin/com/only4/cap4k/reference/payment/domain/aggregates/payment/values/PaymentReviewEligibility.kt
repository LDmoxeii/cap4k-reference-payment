package com.only4.cap4k.reference.payment.domain.aggregates.payment.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata

@DesignBlockMetadata(
    tag = "value_object",
    name = "PaymentReviewEligibility",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.values",
    description = "Derived current automatic-settlement eligibility from append-preserving payment review evidence",
    aggregates = ["Payment"],
    family = "value-object"
)
data class PaymentReviewEligibility(
    val settlementEligible: Boolean,
    val blockingReviewIdentities: List<String>,
    val blockingReviewSummaries: List<String>
)
