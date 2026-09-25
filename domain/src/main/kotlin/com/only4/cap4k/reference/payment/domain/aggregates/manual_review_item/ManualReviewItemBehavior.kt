package com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item

import java.time.LocalDateTime

/** Domain-owned append-only transition for the public ManualReviewItem audit resource. */
data class ManualReviewResolutionAppendResult(
    val resolution: ManualReviewResolution,
    val created: Boolean,
)

fun ManualReviewItem.appendResolution(
    resolutionIdentity: String,
    actorId: String,
    actorRole: String,
    outcome: String,
    reason: String,
    evidence: String,
    resolvedAt: LocalDateTime,
): ManualReviewResolutionAppendResult {
    val existing = manualReviewResolutions.firstOrNull { it.resolutionIdentity == resolutionIdentity }
    if (existing != null) return ManualReviewResolutionAppendResult(existing, created = false)
    require(status == "OPEN") { "ManualReview $id 当前状态为 $status，不能追加处置" }
    val resolution = ManualReviewResolution(
        resolutionIdentity = resolutionIdentity,
        actorId = actorId,
        actorRole = actorRole,
        outcome = outcome,
        reason = reason,
        evidence = evidence,
        resolvedAt = resolvedAt,
    )
    manualReviewResolutions.add(resolution)
    status = "RESOLVED"
    finality = "FINAL"
    this.resolvedAt = resolvedAt
    return ManualReviewResolutionAppendResult(resolution, created = true)
}
