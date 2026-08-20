package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "StatementCompleteness",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class StatementCompleteness(
    val value: Int,
    val description: String
) {

    UNKNOWN(0, "Provider did not establish completeness"),

    INCOMPLETE(1, "Statement is explicitly incomplete"),

    COMPLETE(2, "Statement covers the requested business scope");

    companion object {
        private val enumMap: Map<Int, StatementCompleteness> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): StatementCompleteness? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<StatementCompleteness, Int> {
        override fun convertToDatabaseColumn(attribute: StatementCompleteness?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): StatementCompleteness? {
            return valueOfOrNull(dbData)
        }
    }
}
