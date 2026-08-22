package com.only4.cap4k.reference.payment.application.capabilities.payment.order

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall

@DesignBlockMetadata(
    tag = "capability",
    name = "SerializeMerchantOrderSuccess",
    packageName = "payment.order",
    description = "Serialize accepted-success decisions for one merchant before evaluating the merchant-order claim",
    aggregates = ["Payment", "MerchantChannelConfiguration"],
    family = "capability",
)
object SerializeMerchantOrderSuccess {
    data class Request(
        val merchantId: String,
    ) : CapabilityCall<Response>

    data class Response(
        val lockedConfigurationCount: Int,
    )
}
