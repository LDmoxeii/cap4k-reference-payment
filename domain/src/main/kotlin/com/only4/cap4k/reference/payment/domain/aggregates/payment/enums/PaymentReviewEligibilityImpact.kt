package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentReviewEligibilityImpact",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentReviewEligibilityImpact(
    val value: Int,
    val description: String
) {

    KEEP_BLOCKED(0, "The decision keeps settlement blocked"),

    ALLOW_SETTLEMENT(1, "The decision permits settlement after review");

    companion object {
        private val enumMap: Map<Int, PaymentReviewEligibilityImpact> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentReviewEligibilityImpact? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentReviewEligibilityImpact, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentReviewEligibilityImpact?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentReviewEligibilityImpact? {
            return valueOfOrNull(dbData)
        }
    }
}
