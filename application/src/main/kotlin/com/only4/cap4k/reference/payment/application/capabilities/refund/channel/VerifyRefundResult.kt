package com.only4.cap4k.reference.payment.application.capabilities.refund.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall

@DesignBlockMetadata(
    tag = "capability",
    name = "VerifyRefundResult",
    packageName = "refund.channel",
    description = "Verify refund-channel authenticity without placing credentials in the aggregate",
    aggregates = ["Refund"],
    family = "capability"
)
object VerifyRefundResult {

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
