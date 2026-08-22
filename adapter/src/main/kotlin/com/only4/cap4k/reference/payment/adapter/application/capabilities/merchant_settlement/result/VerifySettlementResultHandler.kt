package com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.result.VerifySettlementResult
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
class VerifySettlementResultHandler : CapabilityHandler<VerifySettlementResult.Request, VerifySettlementResult.Response> {
    override fun call(request: VerifySettlementResult.Request): VerifySettlementResult.Response {
        val normalized = request.result.trim().uppercase()
        val verified = request.channelId == TRUSTED_CHANNEL &&
            request.verificationMaterial == TRUSTED_SECRET &&
            normalized in setOf("SUCCESS", "FAILED", "UNKNOWN")
        return VerifySettlementResult.Response(
            verified = verified,
            normalizedResult = normalized,
            verificationSummary = if (verified) "verified by reference settlement provider" else "untrusted or unsupported settlement result",
        )
    }

    companion object {
        private const val TRUSTED_CHANNEL = "C-001"
        private const val TRUSTED_SECRET = "settlement-secret"
    }
}
