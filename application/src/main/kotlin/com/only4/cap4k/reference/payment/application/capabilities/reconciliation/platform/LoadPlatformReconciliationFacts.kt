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
        val channelId: String,
        val currency: String,
        val reconciliationDate: LocalDate,
        val businessTimezone: String
    ) : CapabilityCall<Response>

    data class Response(
        val facts: List<PlatformReconciliationFact>
    )

}
