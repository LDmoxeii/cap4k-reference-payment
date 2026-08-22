package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentReviewStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentReviewStatus(
    val value: Int,
    val description: String
) {

    OPEN(0, "The review remains unresolved"),

    RESOLVED(1, "An authorized or deterministic decision resolved the review");

    companion object {
        private val enumMap: Map<Int, PaymentReviewStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentReviewStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentReviewStatus, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentReviewStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentReviewStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
