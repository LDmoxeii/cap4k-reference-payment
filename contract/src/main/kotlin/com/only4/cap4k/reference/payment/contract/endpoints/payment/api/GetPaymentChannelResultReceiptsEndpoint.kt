package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.ChannelResultDisposition
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant

/** GET /api/channel/payment-results/{resultIdentity}/receipts */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetPaymentChannelResultReceiptsEndpoint",
    packageName = "payment.api",
    description = "Query authoritative payment channel result receipts by stable external identity",
    aggregates = ["PaymentChannelResultReceipt"],
    operationName = "payment.result.receipts.get",
    family = "endpoint",
)
object GetPaymentChannelResultReceiptsEndpoint {
    const val OPERATION_NAME = "payment.result.receipts.get"

    data class Request(
        val resultIdentity: String,
        val channelId: String? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val resultIdentity: String,
        val totalReceiveCount: Int,
        val receipts: List<Receipt>,
    ) {
        data class Receipt(
            val resultReceiptId: String,
            val resultIdentity: String,
            val payloadIdentity: String,
            val channelId: String,
            val paymentId: String?,
            val paymentAttemptId: String?,
            val channelTransactionId: String,
            val rawEvidence: String,
            val verification: String,
            val verificationSummary: String?,
            val outcome: String,
            val money: Money,
            val occurredAt: Instant,
            val recordedAt: Instant,
            val lastReceivedAt: Instant,
            val receiveCount: Int,
            val disposition: ChannelResultDisposition,
            val rejectionSummary: String?,
        )
    }
}
