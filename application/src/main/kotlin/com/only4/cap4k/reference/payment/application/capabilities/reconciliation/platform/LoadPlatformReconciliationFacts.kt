package com.only4.cap4k.reference.payment.application.capabilities.reconciliation.platform

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.PlatformReconciliationFact
import java.time.LocalDate

@DesignBlockMetadata(
    tag = "capability",
    name = "LoadPlatformReconciliationFacts",
    packageName = "reconciliation.platform",
    description = "Project immutable payment and refund facts for a reconciliation scope without returning aggregate write models",
    aggregates = ["ReconciliationBatch"],
    family = "capability"
)
object LoadPlatformReconciliationFacts {

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
        val businessTimezone: String
    ) : CapabilityCall<Response>

    data class Response(
        /**
         * 事实列表
         */
        val facts: List<PlatformReconciliationFact>
    )

}
