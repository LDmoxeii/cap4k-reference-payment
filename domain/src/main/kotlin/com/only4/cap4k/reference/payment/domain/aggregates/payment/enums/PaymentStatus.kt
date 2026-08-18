package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentStatus(
    val value: Int,
    val description: String
) {

    PENDING(0, "Payment was accepted but no active attempt has completed"),

    PROCESSING(1, "At least one payment attempt is in progress"),

    SUCCEEDED(2, "A verified channel result completed the payment successfully"),

    FAILED(3, "The payment reached an accepted failure result"),

    CLOSED(4, "The payment was closed without success"),

    RESULT_UNKNOWN(5, "The final result requires reconciliation");

    companion object {
        private val enumMap: Map<Int, PaymentStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentStatus, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
