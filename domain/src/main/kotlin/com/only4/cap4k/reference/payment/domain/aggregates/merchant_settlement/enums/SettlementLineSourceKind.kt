package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementLineSourceKind",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementLineSourceKind(
    val value: Int,
    val description: String
) {

    PAYMENT(0, "A verified payment success fact"),

    REFUND(1, "A verified refund success fact"),

    RECONCILIATION_CONFIRMATION(2, "An authorized reconciliation confirmation fact"),

    ADJUSTMENT(3, "A traceable settlement adjustment fact");

    companion object {
        private val enumMap: Map<Int, SettlementLineSourceKind> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementLineSourceKind? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementLineSourceKind, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementLineSourceKind?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementLineSourceKind? {
            return valueOfOrNull(dbData)
        }
    }
}
