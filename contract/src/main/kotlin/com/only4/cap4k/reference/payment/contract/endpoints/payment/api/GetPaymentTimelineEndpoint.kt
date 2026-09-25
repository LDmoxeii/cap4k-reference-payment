package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant

@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetPaymentTimelineEndpoint",
    packageName = "payment.api",
    description = "GET /api/payments/{paymentId}/timeline",
    aggregates = [],
    operationName = "payment.timeline",
    family = "endpoint",
)
object GetPaymentTimelineEndpoint {
    const val OPERATION_NAME = "payment.timeline"

    data class Request(
        val paymentId: String,
        val pageSize: Int? = null,
        val cursor: String? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val items: List<Entry>,
        val nextCursor: String?,
        val pageSize: Int,
    ) {
        data class Entry(
            val eventId: String,
            val eventType: String,
            val category: String,
            val recordedAt: Instant,
            val occurredAt: Instant?,
            val payload: Map<String, Any>,
            val refs: Map<String, String>,
            val relatedResourceRefs: Map<String, String>,
            val outcome: String? = null,
            val actorId: String? = null,
            val reason: String? = null,
            val evidenceRefs: List<String>,
            val money: Money?,
        )
    }
}
