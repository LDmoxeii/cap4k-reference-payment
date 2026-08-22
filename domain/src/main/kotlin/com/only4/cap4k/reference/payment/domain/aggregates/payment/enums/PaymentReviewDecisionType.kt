package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentReviewDecisionType",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentReviewDecisionType(
    val value: Int,
    val description: String
) {

    SYSTEM_ACCEPT_SUCCESS(0, "A deterministic result resolved an unknown review as success"),

    SYSTEM_CONFIRM_FAILURE(1, "A deterministic result resolved an unknown review as failure"),

    ACCEPT_LATE_SUCCESS(2, "An authorized operator accepted trustworthy late success"),

    CONFIRM_FAILURE(3, "An authorized operator confirmed failure for an unknown result"),

    KEEP_CURRENT_TERMINAL(4, "An authorized operator kept the existing closed or failed terminal state"),

    KEEP_ACCEPTED_SUCCESS_WITH_REMEDIATION(5, "An authorized operator kept the first success and recorded remediation");

    companion object {
        private val enumMap: Map<Int, PaymentReviewDecisionType> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentReviewDecisionType? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentReviewDecisionType, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentReviewDecisionType?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentReviewDecisionType? {
            return valueOfOrNull(dbData)
        }
    }
}
