package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant

/** Authoritative, cursor-paged PaymentIntent collection; details remain available from GET /api/payments/{paymentId}. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ListPaymentIntentsEndpoint",
    packageName = "payment.api",
    description = "POST /api/payments/search",
    aggregates = [],
    operationName = "payment.list",
    family = "endpoint",
)
object ListPaymentIntentsEndpoint {
    const val OPERATION_NAME = "payment.list"

    data class Request(
        val merchantId: String? = null,
        /** Unified public status: PAYABLE, PROCESSING, RESULT_UNKNOWN, SUCCEEDED, FAILED, or CLOSED. */
        val status: String? = null,
        val finality: Finality? = null,
        val paymentId: String? = null,
        val merchantOrderId: String? = null,
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
            val paymentId: String,
            val merchantId: String,
            val merchantOrderId: String,
            val money: Money,
            val paymentMethod: String,
            val status: String,
            val finality: Finality,
            /** Immutable creation key used by the collection's DESC keyset order. */
            val sortTime: Instant,
            val expiresAt: Instant,
            val succeededAt: Instant?,
            val channelTransactionId: String?,
            val attemptCount: Int,
            val settlementBlocked: Boolean,
        )
    }
}
