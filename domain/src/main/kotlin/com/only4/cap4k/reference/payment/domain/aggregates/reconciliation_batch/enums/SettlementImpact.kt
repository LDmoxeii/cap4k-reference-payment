package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementImpact",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class SettlementImpact(
    val value: Int,
    val description: String
) {

    BLOCKS_SETTLEMENT(0, "Difference blocks automatic settlement"),

    DOES_NOT_BLOCK_SETTLEMENT(1, "Authorized evidence removes settlement blocking"),

    CONFIRMS_SETTLEMENT_FACT(2, "Disposition creates an additional confirmed funds fact");

    companion object {
        private val enumMap: Map<Int, SettlementImpact> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementImpact? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementImpact, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementImpact?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementImpact? {
            return valueOfOrNull(dbData)
        }
    }
}
