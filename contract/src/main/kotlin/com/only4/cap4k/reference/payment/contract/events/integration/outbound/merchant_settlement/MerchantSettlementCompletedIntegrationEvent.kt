package com.only4.cap4k.reference.payment.contract.events.integration.outbound.merchant_settlement

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.IntegrationEvent
import java.math.BigDecimal
import java.time.Instant

/**
 * A merchant settlement formed its first accepted terminal success fact
 */
@IntegrationEvent(
    value = "payment.merchant-settlement.completed.v1"
)
@DesignBlockMetadata(
    tag = "integration_event",
    name = "MerchantSettlementCompleted",
    packageName = "merchant_settlement",
    description = "A merchant settlement formed its first accepted terminal success fact",
    aggregates = ["MerchantSettlement"],
    eventName = "payment.merchant-settlement.completed.v1",
    family = "integration-event",
    variant = "outbound"
)
data class MerchantSettlementCompletedIntegrationEvent(
    val eventIdentity: String,
    val settlementId: String,
    val merchantId: String,
    val channelId: String,
    val currency: String,
    val netAmount: BigDecimal,
    val completedAt: Instant,
    val correlationIdentity: String? = null,
    val causationIdentity: String? = null
) {
    companion object {
        const val EVENT_NAME = "payment.merchant-settlement.completed.v1"
    }
}
