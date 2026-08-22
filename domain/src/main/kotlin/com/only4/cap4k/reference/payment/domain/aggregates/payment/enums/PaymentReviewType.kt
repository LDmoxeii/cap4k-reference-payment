package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentReviewType",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentReviewType(
    val value: Int,
    val description: String
) {

    EXPIRY_RESULT_UNKNOWN(0, "An expired processing attempt requires result review"),

    LATE_SUCCESS_AFTER_TERMINAL(1, "A trustworthy success arrived after closed or failed terminal state"),

    MULTIPLE_ATTEMPT_SUCCESS(2, "More than one attempt contains trustworthy success evidence"),

    SUCCESS_AFTER_FAILURE_CONFLICT(3, "Success evidence arrived after an accepted failure"),

    FAILURE_OR_UNKNOWN_AFTER_SUCCESS(4, "Failure or unknown evidence arrived after accepted success"),

    NOTIFICATION_PAYLOAD_CONFLICT(5, "One notification identity was reused with a different payload"),

    MERCHANT_ORDER_SUCCESS_CONFLICT(6, "Another payment already owns the merchant-order accepted-success claim");

    companion object {
        private val enumMap: Map<Int, PaymentReviewType> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentReviewType? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentReviewType, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentReviewType?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentReviewType? {
            return valueOfOrNull(dbData)
        }
    }
}
