package com.only4.cap4k.reference.payment.application.capabilities.refund.gateway

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "StartChannelRefund",
    packageName = "refund.gateway",
    description = "Submit a refund attempt to the selected external channel",
    aggregates = ["Refund"],
    family = "capability"
)
object StartChannelRefund {

    data class Request(
        val refundAttemptId: String,
        val channelId: String,
        val requestIdentity: String,
        val amount: BigDecimal,
        val currency: String
    ) : CapabilityCall<Response>

    data class Response(
        val accepted: Boolean,
        val channelRefundId: String?,
        val failureCode: String?,
        val diagnosticSummary: String?
    )

}
