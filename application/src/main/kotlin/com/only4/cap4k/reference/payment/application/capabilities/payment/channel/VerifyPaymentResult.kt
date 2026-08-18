package com.only4.cap4k.reference.payment.application.capabilities.payment.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall

@DesignBlockMetadata(
    tag = "capability",
    name = "VerifyPaymentResult",
    packageName = "payment.channel",
    description = "Verify channel authenticity without placing credentials in the aggregate",
    aggregates = ["Payment"],
    family = "capability"
)
object VerifyPaymentResult {

    data class Request(
        val channelId: String,
        val notificationId: String,
        val payload: String,
        val verificationMaterial: String
    ) : CapabilityCall<Response>

    data class Response(
        val verified: Boolean,
        val verificationSummary: String?
    )

}
