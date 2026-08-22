package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "MerchantSettlementStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class MerchantSettlementStatus(
    val value: Int,
    val description: String
) {

    PREPARING(0, "Settlement composition is being prepared"),

    REVIEW_REQUIRED(1, "Prepared evidence requires operator review"),

    PREPARED(2, "Settlement composition and totals are ready for confirmation"),

    CONFIRMED(3, "Composition is frozen and ready for execution"),

    PROCESSING(4, "An external transfer request was accepted"),

    SUCCEEDED(5, "A verified transfer result completed the settlement"),

    FAILED(6, "The latest transfer attempt failed explicitly"),

    RESULT_UNKNOWN(7, "The transfer result is credible but unknown"),

    NEGATIVE_REVIEW_REQUIRED(8, "Net amount is negative and external transfer is prohibited"),

    VOIDED(9, "The settlement was voided before external execution"),

    CONFLICT_REVIEW_REQUIRED(10, "Conflicting execution evidence requires manual review");

    companion object {
        private val enumMap: Map<Int, MerchantSettlementStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): MerchantSettlementStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<MerchantSettlementStatus, Int> {
        override fun convertToDatabaseColumn(attribute: MerchantSettlementStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): MerchantSettlementStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
