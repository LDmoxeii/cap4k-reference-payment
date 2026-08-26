package com.only4.cap4k.reference.payment.contract.events.integration.inbound.reconciliation

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.IntegrationEvent
import java.time.Instant
import java.time.LocalDate

/**
 * Signal that a revisioned channel statement is available for authoritative pull
 */
@IntegrationEvent(
    value = "payment.reconciliation.channel-statement-available.v1"
)
@DesignBlockMetadata(
    tag = "integration_event",
    name = "ChannelStatementAvailable",
    packageName = "reconciliation",
    description = "Signal that a revisioned channel statement is available for authoritative pull",
    aggregates = ["ReconciliationBatch"],
    eventName = "payment.reconciliation.channel-statement-available.v1",
    family = "integration-event",
    variant = "inbound"
)
data class ChannelStatementAvailableIntegrationEvent(
    /**
     * 身份
     */
    val eventIdentity: String,
    /**
     * 渠道标识
     */
    val channelId: String,
    /**
     * 币种
     */
    val currency: String,
    /**
     * 对账日期
     */
    val reconciliationDate: LocalDate,
    /**
     * 对账单身份
     */
    val statementIdentity: String,
    /**
     * 对账单版本
     */
    val statementRevision: String,
    /**
     * 发布时间
     */
    val publishedAt: Instant,
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
        const val EVENT_NAME = "payment.reconciliation.channel-statement-available.v1"
    }
}
