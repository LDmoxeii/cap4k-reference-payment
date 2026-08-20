package com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.PullChannelStatement
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "PullChannelStatement",
    packageName = "reconciliation.channel",
    description = "Pull one immutable revisioned channel statement for a reconciliation scope",
    aggregates = ["ReconciliationBatch"],
    family = "capability-handler"
)
class PullChannelStatementHandler(
    private val statements: ChannelStatementFixtureStore,
) : CapabilityHandler<PullChannelStatement.Request, PullChannelStatement.Response> {

    override fun call(request: PullChannelStatement.Request): PullChannelStatement.Response {
        val statement = statements.latest(request.channelId, request.currency, request.reconciliationDate)
        require(statement.businessTimezone == request.businessTimezone) {
            "statement timezone ${statement.businessTimezone} does not match ${request.businessTimezone}"
        }
        return PullChannelStatement.Response(statement)
    }
}
