package com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.result.VerifySettlementResult
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceInput
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackKind
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackVerification
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackVerifier
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "VerifySettlementResult",
    packageName = "merchant_settlement.result",
    description = "Verify and normalize one reference settlement result notification",
    aggregates = ["MerchantSettlement"],
    family = "capability-handler"
)
class VerifySettlementResultHandler(
    private val verifier: ReferenceCallbackVerifier,
) : CapabilityHandler<VerifySettlementResult.Request, VerifySettlementResult.Response> {
    override fun call(request: VerifySettlementResult.Request): VerifySettlementResult.Response {
        val normalized = request.result.trim().uppercase()
        val verification = if (normalized in setOf("SUCCESS", "FAILED", "UNKNOWN")) {
            verifier.verify(
                ReferenceCallbackEvidenceInput(
                    kind = ReferenceCallbackKind.SETTLEMENT,
                    channelId = request.channelId,
                    externalIdentity = request.notificationId,
                    associationIdentity = listOf(
                        request.merchantSettlementId,
                        request.executionId,
                        request.executionGroupIdentity,
                        request.requestIdentity,
                        request.externalSettlementIdentity,
                    ).joinToString("|"),
                    amount = request.amount,
                    currency = request.currency,
                    canonicalPayload = request.payload,
                ),
            )
        } else {
            ReferenceCallbackVerification(false, "结算结果类型不受支持")
        }
        return VerifySettlementResult.Response(
            verified = verification.verified,
            normalizedResult = normalized,
            verificationSummary = verification.reason,
        )
    }
}
