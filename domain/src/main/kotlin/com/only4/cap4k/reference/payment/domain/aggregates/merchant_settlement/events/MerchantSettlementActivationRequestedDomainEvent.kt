package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent

/**
 * Activate replacement settlement ownership after the predecessor release has been persisted
 */
@DomainEvent(persist = false)
@DesignBlockMetadata(
    tag = "domain_event",
    name = "MerchantSettlementActivationRequested",
    packageName = "merchant_settlement.lifecycle",
    description = "Activate replacement settlement ownership after the predecessor release has been persisted",
    aggregates = ["MerchantSettlement"],
    family = "domain-event"
)
class MerchantSettlementActivationRequestedDomainEvent(
    val settlementId: String
) {
}
