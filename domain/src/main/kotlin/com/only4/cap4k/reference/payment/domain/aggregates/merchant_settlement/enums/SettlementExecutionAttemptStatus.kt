package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementExecutionAttemptStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementExecutionAttemptStatus(
    val value: Int,
    val description: String
) {

    PROCESSING(0, "The transfer request was accepted and awaits a final result"),

    SUCCEEDED(1, "A verified success result was accepted"),

    FAILED(2, "A verified explicit failure result was accepted"),

    RESULT_UNKNOWN(3, "A verified unknown result was accepted"),

    REVIEW_REQUIRED(4, "The attempt requires authorized manual review"),

    CONFLICT_REVIEW_REQUIRED(5, "Conflicting evidence requires authorized manual review");

    companion object {
        private val enumMap: Map<Int, SettlementExecutionAttemptStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementExecutionAttemptStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementExecutionAttemptStatus, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementExecutionAttemptStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementExecutionAttemptStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
