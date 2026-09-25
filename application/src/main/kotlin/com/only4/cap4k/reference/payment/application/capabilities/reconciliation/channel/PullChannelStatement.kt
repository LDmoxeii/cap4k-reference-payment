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
         * 业务时区
         */
        val businessTimezone: String,
        /**
         * Signal/rerun 可以约束为读取某份权威账单；为空时由 provider 按 scope 选择当前账单。
         */
        val statementIdentity: String? = null,
    ) : CapabilityCall<Response>

    data class Response(
        /**
         * 对账单
         */
        val statement: ChannelStatement
    )

}
