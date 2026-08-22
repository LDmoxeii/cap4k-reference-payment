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
    val eventIdentity: String,
    val channelId: String,
    val currency: String,
    val reconciliationDate: LocalDate,
    val statementIdentity: String,
    val statementRevision: String,
    val publishedAt: Instant,
    val correlationIdentity: String? = null,
    val causationIdentity: String? = null
) {
    companion object {
        const val EVENT_NAME = "payment.reconciliation.channel-statement-available.v1"
    }
}
