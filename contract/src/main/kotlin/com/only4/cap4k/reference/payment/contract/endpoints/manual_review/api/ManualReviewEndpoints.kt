package com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.ManualReviewItemView
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import java.time.Instant

@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetManualReviewEndpoint",
    packageName = "manual_review.api",
    description = "GET /api/manual-reviews/{reviewId}",
    aggregates = [],
    operationName = "manual-review.get",
    family = "endpoint",
)
object GetManualReviewEndpoint {
    const val OPERATION_NAME = "manual-review.get"

    data class Request(val reviewId: String) : EndpointRequest<Response>
    data class Response(val review: ManualReviewItemView)
}

/** JSON search keeps all cursor-bound filters optional without controller-side aggregate materialisation. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ListManualReviewsEndpoint",
    packageName = "manual_review.api",
    description = "POST /api/manual-reviews/search",
    aggregates = [],
    operationName = "manual-review.list",
    family = "endpoint",
)
object ListManualReviewsEndpoint {
    const val OPERATION_NAME = "manual-review.list"

    data class Request(
        val merchantId: String? = null,
        val type: String? = null,
        val status: String? = null,
        val finality: Finality? = null,
        val originKind: String? = null,
        val originIdentity: String? = null,
        val createdFrom: Instant? = null,
        val createdTo: Instant? = null,
        val cursor: String? = null,
        val pageSize: Int? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val items: List<Item>,
        val nextCursor: String?,
        val pageSize: Int,
    ) {
        data class Item(
            val reviewId: String,
            val reviewIdentity: String,
            val type: String,
            val status: String,
            val finality: Finality,
            val merchantId: String?,
            val originKind: String,
            val originIdentity: String,
            val summary: String,
            val sortTime: Instant,
            val resolvedAt: Instant?,
        )
    }
}

/**
 * POST /api/manual-reviews/{reviewId}/resolutions.  `actorId` is deliberately absent: the adapter
 * obtains it only from the trusted reference actor context header.
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ResolveManualReviewEndpoint",
    packageName = "manual_review.api",
    description = "POST /api/manual-reviews/{reviewId}/resolutions",
    aggregates = [],
    operationName = "manual-review.resolve",
    family = "endpoint",
)
object ResolveManualReviewEndpoint {
    const val OPERATION_NAME = "manual-review.resolve"

    data class Request(
        val reviewId: String = "",
        /** Independent command identity; replay is governed by the authoritative Operation record. */
        val idempotencyKey: String,
        /** Optional business-scope assertion for items whose source is not merchant-owned. */
        val merchantId: String? = null,
        /** Source-specific final conclusion; only values accepted by that source aggregate are valid. */
        val outcome: String,
        val reason: String,
        val evidence: String,
        /** Needed only by payment's existing remediation decision and never treated as actor evidence. */
        val remediationReference: String? = null,
        /** Needed only when a reconciliation confirmation cannot derive merchant/channel from weak refs. */
        val channelId: String? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val review: ManualReviewItemView,
        val receipt: OperationReceipt,
    )
}
