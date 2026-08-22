package com.only4.cap4k.reference.payment.application.subscribers.integration

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.reconciliation.integration.ProcessAvailableChannelStatementCmd
import com.only4.cap4k.reference.payment.contract.events.integration.inbound.reconciliation.ChannelStatementAvailableIntegrationEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * Signal that a revisioned channel statement is available for authoritative pull
 */
@Service
@DesignBlockMetadata(
    tag = "integration_event",
    name = "ChannelStatementAvailable",
    packageName = "reconciliation",
    description = "Signal that a revisioned channel statement is available for authoritative pull",
    aggregates = ["ReconciliationBatch"],
    family = "integration-subscriber"
)
class ChannelStatementAvailableIntegrationEventSubscriber {

    @EventListener(ChannelStatementAvailableIntegrationEvent::class)
    fun on(event: ChannelStatementAvailableIntegrationEvent) {
        Mediator.commands.send(
            ProcessAvailableChannelStatementCmd.Request(
                eventIdentity = event.eventIdentity,
                channelId = event.channelId,
                currency = event.currency,
                reconciliationDate = event.reconciliationDate,
                statementIdentity = event.statementIdentity,
                statementRevision = event.statementRevision,
                publishedAt = event.publishedAt,
                correlationIdentity = event.correlationIdentity,
                causationIdentity = event.causationIdentity,
            )
        )
    }
}
