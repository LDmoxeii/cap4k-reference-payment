package com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "capability",
    name = "VerifySettlementResult",
    packageName = "merchant_settlement.result",
    description = "Verify and normalize one reference settlement result notification",
    aggregates = ["MerchantSettlement"],
    family = "capability"
)
object VerifySettlementResult {

    data class Request(
        val channelId: String,
        val notificationId: String,
        val settlementId: String,
        val executionAttemptId: String,
        val executionGroupIdentity: String,
        val requestIdentity: String,
        val externalSettlementIdentity: String,
        val amount: BigDecimal,
        val currency: String,
        val result: String,
        val resultCode: String?,
        val occurredAt: Instant,
        val verificationMaterial: String
    ) : CapabilityCall<Response>

    data class Response(
        val verified: Boolean,
        val normalizedResult: String,
        val verificationSummary: String?
    )

}
