package com.only4.cap4k.reference.payment.application.subscribers.domain.merchant_settlement

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.contract.events.integration.outbound.merchant_settlement.MerchantSettlementCompletedIntegrationEvent
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events.MerchantSettlementCompletedDomainEvent
import java.time.ZoneOffset
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * A merchant settlement formed its first accepted terminal success fact
 */
@Service
@DesignBlockMetadata(
    tag = "domain_event",
    name = "MerchantSettlementCompleted",
    packageName = "merchant_settlement.completion",
    description = "A merchant settlement formed its first accepted terminal success fact",
    aggregates = ["MerchantSettlement"],
    family = "domain-subscriber"
)
class MerchantSettlementCompletedDomainEventSubscriber {

    @EventListener(MerchantSettlementCompletedDomainEvent::class)
    fun on(event: MerchantSettlementCompletedDomainEvent) {
        Mediator.events.enqueue(
            MerchantSettlementCompletedIntegrationEvent(
                eventIdentity = event.eventIdentity,
                settlementId = event.settlementId,
                merchantId = event.merchantId,
                channelId = event.channelId,
                currency = event.currency,
                netAmount = event.netAmount,
                completedAt = event.completedAt.toInstant(ZoneOffset.UTC),
                correlationIdentity = event.settlementId,
                causationIdentity = event.eventIdentity,
            )
        )
    }
}
