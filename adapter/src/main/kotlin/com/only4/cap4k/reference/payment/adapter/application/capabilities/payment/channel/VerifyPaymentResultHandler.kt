package com.only4.cap4k.reference.payment.adapter.application.capabilities.payment.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.payment.channel.VerifyPaymentResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "VerifyPaymentResult",
    packageName = "payment.channel",
    description = "Verify channel authenticity without placing credentials in the aggregate",
    aggregates = ["Payment"],
    family = "capability-handler"
)
class VerifyPaymentResultHandler(
    @param:Value("\${payment.sandbox.channel-id}") private val trustedChannelId: String,
    @param:Value("\${payment.sandbox.verification-secret}") private val verificationSecret: String,
) : CapabilityHandler<VerifyPaymentResult.Request, VerifyPaymentResult.Response> {

    override fun call(request: VerifyPaymentResult.Request): VerifyPaymentResult.Response {
        val verified = request.channelId == trustedChannelId &&
            request.verificationMaterial == verificationSecret &&
            request.notificationId.isNotBlank() &&
            request.payload.isNotBlank()
        return VerifyPaymentResult.Response(
            verified = verified,
            verificationSummary = if (verified) "sandbox signature verified" else "sandbox signature verification failed",
        )
    }
}
