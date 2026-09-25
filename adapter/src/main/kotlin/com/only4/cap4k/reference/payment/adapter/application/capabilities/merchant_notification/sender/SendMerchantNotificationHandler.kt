package com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_notification.sender

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceMerchantNotificationSenderRegistry
import com.only4.cap4k.reference.payment.application.capabilities.merchant_notification.sender.SendMerchantNotification
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "SendMerchantNotification",
    packageName = "merchant_notification.sender",
    description = "Deliver frozen merchant notification content under a stable delivery identity",
    aggregates = ["MerchantNotification"],
    family = "capability-handler",
)
class SendMerchantNotificationHandler(
    private val registry: ReferenceMerchantNotificationSenderRegistry,
) : CapabilityHandler<SendMerchantNotification.Request, SendMerchantNotification.Response> {
    override fun call(request: SendMerchantNotification.Request): SendMerchantNotification.Response =
        registry.send(request)
}
