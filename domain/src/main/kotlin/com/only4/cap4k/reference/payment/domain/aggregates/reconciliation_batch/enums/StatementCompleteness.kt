package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "StatementCompleteness",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "渠道对账单完整性",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class StatementCompleteness(
    val value: Int,
    val description: String
) {

    UNKNOWN(0, "渠道提供方未能确认对账单完整性"),

    INCOMPLETE(1, "对账单已明确标记为不完整"),

    COMPLETE(2, "对账单覆盖了请求的业务范围");

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
