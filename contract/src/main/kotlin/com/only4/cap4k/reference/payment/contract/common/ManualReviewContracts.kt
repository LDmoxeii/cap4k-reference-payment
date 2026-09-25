package com.only4.cap4k.reference.payment.contract.common

import java.time.Instant

/** A backend-owned resource reference shown to the operator; it is never a conclusion by itself. */
data class ManualReviewReference(
    val resourceType: String,
    val resourceId: String,
)

/** A business scope currently blocked by an unresolved review item. */
data class ManualReviewBlockingScope(
    val scopeType: String,
    val scopeId: String,
)

/** An immutable evidence reference associated with the originating fact. */
data class ManualReviewEvidenceRef(
    val evidenceType: String,
    val evidenceId: String,
    val summary: String? = null,
)

/** Append-only resolution history. Resolution command handling is intentionally added separately. */
data class ManualReviewResolutionView(
    val resolutionId: String,
    val resolutionIdentity: String,
    val actorId: String,
    val actorRole: String,
    val outcome: String,
    val reason: String,
    val evidence: String,
    val resolvedAt: Instant,
)

/** The public, normalized manual-review representation; type/status are protocol values, not JPA enums. */
data class ManualReviewItemView(
    val reviewId: String,
    val reviewIdentity: String,
    val type: String,
    val status: String,
    val finality: Finality,
    val merchantId: String?,
    val originKind: String,
    val originIdentity: String,
    val summary: String,
    val relatedRefs: List<ManualReviewReference>,
    val blockingScopes: List<ManualReviewBlockingScope>,
    val evidenceRefs: List<ManualReviewEvidenceRef>,
    val sortTime: Instant,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val resolutions: List<ManualReviewResolutionView>,
)
