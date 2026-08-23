package com.only4.cap4k.reference.payment.application.subscribers.domain.merchant_settlement

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.lifecycle.ActivateMerchantSettlementCmd
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events.MerchantSettlementActivationRequestedDomainEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * 在前置结算单释放所有权后激活替代结算单的有效所有权
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
    /** 通过独立 Command 在同一事务边界激活 replacement；失败时由 UoW 回滚 predecessor 与 replacement 组合。 */
    fun on(event: MerchantSettlementActivationRequestedDomainEvent) {
        Mediator.commands.send(ActivateMerchantSettlementCmd.Request(event.settlementId))
    }
}
