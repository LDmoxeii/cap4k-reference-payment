package com.only4.cap4k.reference.payment.adapter.application.capabilities.payment.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.payment.channel.VerifyPaymentResult
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceInput
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackKind
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackVerifier
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
    private val verifier: ReferenceCallbackVerifier,
) : CapabilityHandler<VerifyPaymentResult.Request, VerifyPaymentResult.Response> {

    override fun call(request: VerifyPaymentResult.Request): VerifyPaymentResult.Response {
        val verification = verifier.verify(
            ReferenceCallbackEvidenceInput(
                kind = ReferenceCallbackKind.PAYMENT,
                channelId = request.channelId,
                externalIdentity = request.notificationId,
                associationIdentity = listOf(
                    request.paymentId,
                    request.paymentAttemptId,
                    request.channelTransactionId,
                ).joinToString("|"),
                amount = request.amount,
                currency = request.currency,
                canonicalPayload = request.payload,
            ),
        )
        return VerifyPaymentResult.Response(
            verified = verification.verified,
            verificationSummary = verification.reason,
        )
    }
}
