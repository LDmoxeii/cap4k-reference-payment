package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentAttemptStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentAttemptStatus(
    val value: Int,
    val description: String
) {

    PROCESSING(0, "The channel attempt is in progress"),

    SUCCEEDED(1, "The channel attempt completed successfully"),

    FAILED(2, "The channel attempt completed unsuccessfully");

    companion object {
        private val enumMap: Map<Int, PaymentAttemptStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentAttemptStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentAttemptStatus, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentAttemptStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentAttemptStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
