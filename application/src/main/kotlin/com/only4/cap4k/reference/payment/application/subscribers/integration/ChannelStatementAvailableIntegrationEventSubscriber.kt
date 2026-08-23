package com.only4.cap4k.reference.payment.application.subscribers.integration

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.commands.reconciliation.integration.ProcessAvailableChannelStatementCmd
import com.only4.cap4k.reference.payment.contract.events.integration.inbound.reconciliation.ChannelStatementAvailableIntegrationEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * 通知应用某个账单 revision 已可供权威拉取；事件本身不携带账单正文
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
    /** 薄 listener 只传递标量和追踪身份并发送 Command，Repository 与 provider Pull 均留在应用路径。 */
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
