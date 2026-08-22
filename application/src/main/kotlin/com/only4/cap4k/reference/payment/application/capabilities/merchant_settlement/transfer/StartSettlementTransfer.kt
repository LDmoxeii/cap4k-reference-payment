package com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.transfer

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "StartSettlementTransfer",
    packageName = "merchant_settlement.transfer",
    description = "Submit one idempotent settlement transfer request to the reference provider",
    aggregates = ["MerchantSettlement"],
    family = "capability"
)
object StartSettlementTransfer {

    data class Request(
        val settlementId: String,
        val executionAttemptId: String,
        val merchantId: String,
        val channelId: String,
        val executionGroupIdentity: String,
        val requestIdentity: String,
        val amount: BigDecimal,
        val currency: String
    ) : CapabilityCall<Response>

    data class Response(
        val accepted: Boolean,
        val externalSettlementIdentity: String?,
        val failureCode: String?,
        val diagnosticSummary: String?
    )

}
