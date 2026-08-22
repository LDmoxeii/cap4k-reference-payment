package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementExecutionFinalResult",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementExecutionFinalResult(
    val value: Int,
    val description: String
) {

    SUCCESS(0, "The external settlement completed successfully"),

    FAILED(1, "The external settlement failed explicitly"),

    GATEWAY_REJECTED(2, "The transfer request was rejected before processing"),

    UNKNOWN(3, "The external settlement result is unknown");

    companion object {
        private val enumMap: Map<Int, SettlementExecutionFinalResult> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementExecutionFinalResult? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementExecutionFinalResult, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementExecutionFinalResult?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementExecutionFinalResult? {
            return valueOfOrNull(dbData)
        }
    }
}
