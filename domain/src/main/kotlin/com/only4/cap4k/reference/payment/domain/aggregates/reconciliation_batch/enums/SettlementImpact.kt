package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementImpact",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "对账差异对结算的影响",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class SettlementImpact(
    val value: Int,
    val description: String
) {

    BLOCKS_SETTLEMENT(0, "差异阻断自动结算"),

    DOES_NOT_BLOCK_SETTLEMENT(1, "经授权的证据解除结算阻断"),

    CONFIRMS_SETTLEMENT_FACT(2, "处置产生额外的已确认资金事实");

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
