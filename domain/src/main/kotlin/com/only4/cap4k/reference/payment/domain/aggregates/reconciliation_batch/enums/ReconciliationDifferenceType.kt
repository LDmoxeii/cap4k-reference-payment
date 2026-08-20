package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationDifferenceType",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationDifferenceType(
    val value: Int,
    val description: String
) {

    MATCHED(0, "Platform and channel evidence matched"),

    PLATFORM_ONLY(1, "Only the platform fact exists"),

    CHANNEL_ONLY(2, "Only the channel record exists"),

    AMOUNT_MISMATCH(3, "Stable identities matched but amounts differ"),

    CURRENCY_MISMATCH(4, "Stable identities matched but currencies differ"),

    STATUS_MISMATCH(5, "Stable identities matched but final statuses differ"),

    DUPLICATE_CHANNEL_RECORD(6, "The statement repeats a stable channel record"),

    UNMATCHED(7, "No approved stable association could be formed");

    companion object {
        private val enumMap: Map<Int, ReconciliationDifferenceType> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationDifferenceType? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationDifferenceType, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationDifferenceType?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationDifferenceType? {
            return valueOfOrNull(dbData)
        }
    }
}
