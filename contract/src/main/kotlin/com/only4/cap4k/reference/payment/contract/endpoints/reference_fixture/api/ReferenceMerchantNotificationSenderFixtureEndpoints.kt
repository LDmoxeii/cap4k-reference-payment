package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfigureReferenceMerchantNotificationSenderEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/merchant-notification-sender-script",
    aggregates = [],
    operationName = "reference-fixture.merchant-notification-sender.configure",
    family = "endpoint",
)
object ConfigureReferenceMerchantNotificationSenderEndpoint {
    const val OPERATION_NAME = "reference-fixture.merchant-notification-sender.configure"
    data class Request(
        val notificationIdentity: String? = null,
        val sourceKind: String? = null,
        val sourceFactIdentity: String? = null,
        val script: String,
    ) : EndpointRequest<Response>
    data class Response(
        val notificationIdentity: String?,
        val sourceKind: String?,
        val sourceFactIdentity: String?,
        val script: String,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ResetReferenceMerchantNotificationSenderEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/merchant-notification-sender-script/reset",
    aggregates = [],
    operationName = "reference-fixture.merchant-notification-sender.reset",
    family = "endpoint",
)
object ResetReferenceMerchantNotificationSenderEndpoint {
    const val OPERATION_NAME = "reference-fixture.merchant-notification-sender.reset"
    data class Request(
        val notificationIdentity: String? = null,
        val sourceKind: String? = null,
        val sourceFactIdentity: String? = null,
    ) : EndpointRequest<Response>
    data class Response(
        val notificationIdentity: String?,
        val sourceKind: String?,
        val sourceFactIdentity: String?,
        val script: String,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ClearReferenceMerchantNotificationSenderEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/merchant-notification-sender-script/clear",
    aggregates = [],
    operationName = "reference-fixture.merchant-notification-sender.clear",
    family = "endpoint",
)
object ClearReferenceMerchantNotificationSenderEndpoint {
    const val OPERATION_NAME = "reference-fixture.merchant-notification-sender.clear"
    data class Request(val clear: Boolean = true) : EndpointRequest<Response>
    data class Response(val cleared: Boolean)
}
