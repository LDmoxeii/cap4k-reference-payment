package com.only4.cap4k.reference.payment.application.capabilities.payment.gateway

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "StartChannelPayment",
    packageName = "payment.gateway",
    description = "Submit a payment attempt to the selected external channel",
    aggregates = ["Payment"],
    family = "capability"
)
object StartChannelPayment {

    data class Request(
        val paymentAttemptId: String,
        val channelId: String,
        val requestIdentity: String,
        val amount: BigDecimal,
        val currency: String
    ) : CapabilityCall<Response>

    data class Response(
        val accepted: Boolean,
        val channelReference: String?,
        val failureCode: String?,
        val diagnosticSummary: String?
    )

}
