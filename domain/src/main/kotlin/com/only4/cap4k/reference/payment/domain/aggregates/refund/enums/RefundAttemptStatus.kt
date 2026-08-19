package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundAttemptStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundAttemptStatus(
    val value: Int,
    val description: String
) {

    PROCESSING(0, "The channel refund attempt is in progress"),

    SUCCEEDED(1, "The channel refund attempt completed successfully"),

    FAILED(2, "The channel refund attempt completed unsuccessfully"),

    RESULT_UNKNOWN(3, "The channel returned a trusted but non-final result"),

    REVIEW_REQUIRED(4, "The attempt requires review after the configured threshold");

    companion object {
        private val enumMap: Map<Int, RefundAttemptStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundAttemptStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundAttemptStatus, Int> {
        override fun convertToDatabaseColumn(attribute: RefundAttemptStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundAttemptStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
