package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationDispositionConclusion",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationDispositionConclusion(
    val value: Int,
    val description: String
) {

    ACCEPT_AS_MATCHED(0, "Evidence establishes an acceptable match"),

    CONFIRM_PLATFORM_FACT(1, "Channel evidence authorizes a new platform confirmation fact"),

    ACCEPT_CHANNEL_FACT(2, "Channel-only fact is accepted with evidence"),

    NO_SETTLEMENT_IMPACT(3, "Difference is documented as not affecting settlement"),

    ESCALATE(4, "Difference remains open for follow-up");

    companion object {
        private val enumMap: Map<Int, ReconciliationDispositionConclusion> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationDispositionConclusion? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationDispositionConclusion, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationDispositionConclusion?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationDispositionConclusion? {
            return valueOfOrNull(dbData)
        }
    }
}
