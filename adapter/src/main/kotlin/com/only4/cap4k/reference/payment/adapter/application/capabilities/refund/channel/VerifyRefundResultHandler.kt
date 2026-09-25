package com.only4.cap4k.reference.payment.adapter.application.capabilities.refund.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.refund.channel.VerifyRefundResult
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceInput
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackKind
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackVerifier
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "VerifyRefundResult",
    packageName = "refund.channel",
    description = "Verify refund-channel authenticity without placing credentials in the aggregate",
    aggregates = ["Refund"],
    family = "capability-handler"
)
class VerifyRefundResultHandler(
    private val verifier: ReferenceCallbackVerifier,
) : CapabilityHandler<VerifyRefundResult.Request, VerifyRefundResult.Response> {
    override fun call(request: VerifyRefundResult.Request): VerifyRefundResult.Response {
        val verification = verifier.verify(
            ReferenceCallbackEvidenceInput(
                kind = ReferenceCallbackKind.REFUND,
                channelId = request.channelId,
                externalIdentity = request.notificationId,
                associationIdentity = listOf(
                    request.refundId,
                    request.refundAttemptId,
                    request.channelRefundId,
                ).joinToString("|"),
                amount = request.amount,
                currency = request.currency,
                canonicalPayload = request.payload,
            ),
        )
        return VerifyRefundResult.Response(
            verified = verification.verified,
            verificationSummary = verification.reason,
        )
    }
}
