package com.only4.cap4k.reference.payment.application.subscribers.domain.merchant_settlement

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.merchant_notification.CreateMerchantNotificationForFactCmd
import com.only4.cap4k.reference.payment.contract.events.integration.outbound.merchant_settlement.MerchantSettlementCompletedIntegrationEvent
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events.MerchantSettlementCompletedDomainEvent
import java.time.ZoneOffset
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * 商户结算单首次形成已接受的终态成功事实
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
    /** 映射为稳定 published event 并交给 reliable Event/JPA；这里不直接调用 HTTP 或创建第二个业务 identity。 */
    fun on(event: MerchantSettlementCompletedDomainEvent) {
        Mediator.commands.send(
            CreateMerchantNotificationForFactCmd.Request(
                merchantId = event.merchantId,
                sourceKind = "SETTLEMENT",
                sourceFactIdentity = event.eventIdentity,
                paymentId = null,
                content = mapOf(
                    "merchantId" to event.merchantId,
                    "settlementId" to event.merchantSettlementId.toString(),
                    "amount" to event.netAmount.toPlainString(),
                    "currency" to event.currency,
                    "status" to "SUCCEEDED",
                    "settlementPeriodStart" to event.periodStart.toString(),
                    "settlementPeriodEnd" to event.periodEnd.toString(),
                ),
            ),
        )
        Mediator.events.enqueue(
            MerchantSettlementCompletedIntegrationEvent(
                eventIdentity = event.eventIdentity,
                settlementId = event.merchantSettlementId.toString(),
                merchantId = event.merchantId,
                executionChannelId = event.executionChannelId,
                currency = event.currency,
                netAmount = event.netAmount,
                completedAt = event.completedAt.toInstant(ZoneOffset.UTC),
                correlationIdentity = event.merchantSettlementId.toString(),
                causationIdentity = event.eventIdentity,
            )
        )
    }
}
