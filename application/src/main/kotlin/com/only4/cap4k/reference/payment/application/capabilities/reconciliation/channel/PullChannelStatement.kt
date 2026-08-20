package com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import java.time.LocalDate

@DesignBlockMetadata(
    tag = "capability",
    name = "PullChannelStatement",
    packageName = "reconciliation.channel",
    description = "Pull one immutable revisioned channel statement for a reconciliation scope",
    aggregates = ["ReconciliationBatch"],
    family = "capability"
)
object PullChannelStatement {

    data class Request(
        val channelId: String,
        val currency: String,
        val reconciliationDate: LocalDate,
        val businessTimezone: String
    ) : CapabilityCall<Response>

    data class Response(
        val statement: ChannelStatement
    )

}
