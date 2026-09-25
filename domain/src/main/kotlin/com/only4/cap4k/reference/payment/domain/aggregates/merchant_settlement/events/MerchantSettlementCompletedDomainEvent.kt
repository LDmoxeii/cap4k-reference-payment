package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A merchant settlement formed its first accepted terminal success fact
 */
@DomainEvent(persist = false)
@DesignBlockMetadata(
    tag = "domain_event",
    name = "MerchantSettlementCompleted",
    packageName = "merchant_settlement.completion",
    description = "A merchant settlement formed its first accepted terminal success fact",
    aggregates = ["MerchantSettlement"],
    family = "domain-event"
)
class MerchantSettlementCompletedDomainEvent(
    val eventIdentity: String,
    val merchantSettlementId: MerchantSettlementId,
    val merchantId: String,
    /** 执行渠道仅为执行证据；不定义结算 scope。 */
    val executionChannelId: String?,
    val currency: String,
    val netAmount: BigDecimal,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
    val completedAt: LocalDateTime
) {
}
