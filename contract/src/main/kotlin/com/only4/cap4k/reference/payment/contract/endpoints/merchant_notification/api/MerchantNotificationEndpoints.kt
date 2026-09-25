package com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import java.time.Instant

data class MerchantNotificationView(
    val notificationId: String,
    val notificationIdentity: String,
    val contentIdentity: String,
    val merchantId: String,
    val sourceKind: String,
    val sourceFactIdentity: String,
    val paymentId: String?,
    val content: String,
    val status: String,
    val finality: Finality,
    val maxAttempts: Int,
    val createdAt: Instant,
    val deliveryAttempts: List<DeliveryAttempt>,
) {
    data class DeliveryAttempt(
        val deliveryAttemptId: String,
        val attemptSequence: Int,
        val deliveryIdentity: String,
        val contentIdentity: String,
        val outcome: String,
        val diagnostic: String?,
        val recordedAt: Instant,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetMerchantNotificationEndpoint",
    packageName = "merchant_notification.api",
    description = "GET /api/merchant-notifications/{notificationId}",
    aggregates = [],
    operationName = "merchant-notification.get",
    family = "endpoint",
)
object GetMerchantNotificationEndpoint {
    const val OPERATION_NAME = "merchant-notification.get"
    data class Request(val notificationId: String) : EndpointRequest<Response>
    data class Response(val notification: MerchantNotificationView)
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ListMerchantNotificationsEndpoint",
    packageName = "merchant_notification.api",
    description = "POST /api/merchant-notifications/search",
    aggregates = [],
    operationName = "merchant-notification.list",
    family = "endpoint",
)
object ListMerchantNotificationsEndpoint {
    const val OPERATION_NAME = "merchant-notification.list"
    data class Request(
        val merchantId: String? = null,
        val paymentId: String? = null,
        val sourceKind: String? = null,
        val status: String? = null,
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
            val notificationId: String,
            val notificationIdentity: String,
            val contentIdentity: String,
            val merchantId: String,
            val sourceKind: String,
            val sourceFactIdentity: String,
            val paymentId: String?,
            val status: String,
            val finality: Finality,
            val sortTime: Instant,
            val deliveryAttemptCount: Int,
        )
    }
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "RetryMerchantNotificationEndpoint",
    packageName = "merchant_notification.api",
    description = "POST /api/merchant-notifications/{notificationId}/retries",
    aggregates = [],
    operationName = "merchant-notification.retry",
    family = "endpoint",
)
object RetryMerchantNotificationEndpoint {
    const val OPERATION_NAME = "merchant-notification.retry"
    data class Request(
        val notificationId: String = "",
        val idempotencyKey: String,
    ) : EndpointRequest<Response>
    data class Response(
        val notificationId: String,
        val receipt: OperationReceipt,
    )
}
