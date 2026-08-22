package com.only4.cap4k.reference.payment.application.subscribers.domain.merchant_settlement

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.lifecycle.ActivateMerchantSettlementCmd
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events.MerchantSettlementActivationRequestedDomainEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * Activate replacement settlement ownership after the predecessor release has been persisted
 */
@Service
@DesignBlockMetadata(
    tag = "domain_event",
    name = "MerchantSettlementActivationRequested",
    packageName = "merchant_settlement.lifecycle",
    description = "Activate replacement settlement ownership after the predecessor release has been persisted",
    aggregates = ["MerchantSettlement"],
    family = "domain-subscriber"
)
class MerchantSettlementActivationRequestedDomainEventSubscriber {

    @EventListener(MerchantSettlementActivationRequestedDomainEvent::class)
    fun on(event: MerchantSettlementActivationRequestedDomainEvent) {
        Mediator.commands.send(ActivateMerchantSettlementCmd.Request(event.settlementId))
    }
}
