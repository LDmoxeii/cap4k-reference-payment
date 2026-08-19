package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundStatus(
    val value: Int,
    val description: String
) {

    PROCESSING(0, "Refund budget is reserved and a channel request is in progress"),

    SUCCEEDED(1, "A verified channel result completed the refund successfully"),

    FAILED(2, "The refund failed and its reserved payment budget was released"),

    RESULT_UNKNOWN(3, "A trusted channel result is not yet final and budget remains reserved"),

    REVIEW_REQUIRED(4, "The accepted refund request exceeded its review threshold without a final result");

    companion object {
        private val enumMap: Map<Int, RefundStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundStatus, Int> {
        override fun convertToDatabaseColumn(attribute: RefundStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
