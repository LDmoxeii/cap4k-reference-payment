package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundAttemptFinalResult",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundAttemptFinalResult(
    val value: Int,
    val description: String
) {

    SUCCESS(0, "The channel reported a verified successful refund"),

    FAILED(1, "The channel reported a verified failed refund"),

    GATEWAY_REJECTED(2, "The gateway rejected the refund request before processing");

    companion object {
        private val enumMap: Map<Int, RefundAttemptFinalResult> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundAttemptFinalResult? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundAttemptFinalResult, Int> {
        override fun convertToDatabaseColumn(attribute: RefundAttemptFinalResult?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundAttemptFinalResult? {
            return valueOfOrNull(dbData)
        }
    }
}
