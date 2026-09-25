package com.only4.cap4k.reference.payment.application.capabilities.merchant_notification.sender

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotificationId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationDeliveryOutcome

@DesignBlockMetadata(
    tag = "capability",
    name = "SendMerchantNotification",
    packageName = "merchant_notification.sender",
    description = "Deliver frozen merchant notification content under a stable delivery identity",
    aggregates = ["MerchantNotification"],
    family = "capability",
)
object SendMerchantNotification {
    data class Request(
        val notificationId: MerchantNotificationId,
        val notificationIdentity: String,
        val contentIdentity: String,
        val deliveryIdentity: String,
        val merchantId: String,
        val sourceKind: String,
        val sourceFactIdentity: String,
        val content: String,
    ) : CapabilityCall<Response>

    data class Response(
        val outcome: MerchantNotificationDeliveryOutcome,
        val diagnostic: String?,
    )
}
