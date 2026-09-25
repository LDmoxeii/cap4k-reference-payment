package com.only4.cap4k.reference.payment.contract.endpoints.refund.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant

/** Authoritative Refund collection.  Attempts and callback receipts remain child-resource observations. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ListRefundsEndpoint",
    packageName = "refund.api",
    description = "POST /api/refunds/search",
    aggregates = [],
    operationName = "refund.list",
    family = "endpoint",
)
object ListRefundsEndpoint {
    const val OPERATION_NAME = "refund.list"

    data class Request(
        val merchantId: String? = null,
        /** Unified public status: REQUESTED, PROCESSING, RESULT_UNKNOWN, SUCCEEDED, FAILED, or REJECTED. */
        val status: String? = null,
        val finality: Finality? = null,
        val paymentId: String? = null,
        val refundId: String? = null,
        val merchantRefundId: String? = null,
        val channelId: String? = null,
        val currency: String? = null,
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
            val refundId: String,
            val paymentId: String,
            val merchantId: String,
            val merchantRefundId: String,
            val money: Money,
            val reason: String,
            val status: String,
            val finality: Finality,
            val sortTime: Instant,
            val channelId: String?,
            val channelRefundId: String?,
            val reservationActive: Boolean,
            val settlementBlocked: Boolean,
        )
    }
}
