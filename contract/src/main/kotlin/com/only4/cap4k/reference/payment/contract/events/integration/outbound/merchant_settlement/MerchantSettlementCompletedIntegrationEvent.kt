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
    /**
     * 身份
     */
    val eventIdentity: String,
    /**
     * 结算标识
     */
    val settlementId: String,
    /**
     * 商户标识
     */
    val merchantId: String,
    /**
     * 渠道标识
     */
    val channelId: String,
    /**
     * 币种
     */
    val currency: String,
    /**
     * 净金额
     */
    val netAmount: BigDecimal,
    /**
     * 完成时间
     */
    val completedAt: Instant,
    /**
     * 关联身份
     */
    val correlationIdentity: String? = null,
    /**
     * 因果身份
     */
    val causationIdentity: String? = null
) {
    companion object {
        const val EVENT_NAME = "payment.merchant-settlement.completed.v1"
    }
}
