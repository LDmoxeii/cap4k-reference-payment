package com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item

import java.time.LocalDateTime

data class ManualReviewResolutionCreation(
    val resolutionIdentity: String,
    val actorId: String,
    val actorRole: String,
    val outcome: String,
    val reason: String,
    val evidence: String,
    val resolvedAt: LocalDateTime
)
