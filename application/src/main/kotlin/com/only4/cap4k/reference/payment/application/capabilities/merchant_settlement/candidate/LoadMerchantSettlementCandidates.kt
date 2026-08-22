package com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.candidate

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementCandidateFact
import java.time.Instant

@DesignBlockMetadata(
    tag = "capability",
    name = "LoadMerchantSettlementCandidates",
    packageName = "merchant_settlement.candidate",
    description = "Project eligible and excluded current-effective-run facts for a merchant settlement period",
    aggregates = ["MerchantSettlement"],
    family = "capability"
)
object LoadMerchantSettlementCandidates {

    data class Request(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val periodStart: Instant,
        val periodEnd: Instant,
        val businessTimezone: String
    ) : CapabilityCall<Response>

    data class Response(
        val eligibleFacts: List<SettlementCandidateFact>,
        val excludedCount: Int,
        val blockerSummaries: List<String>
    )

}
