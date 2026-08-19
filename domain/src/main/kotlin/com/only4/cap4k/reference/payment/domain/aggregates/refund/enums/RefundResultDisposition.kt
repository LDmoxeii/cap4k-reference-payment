package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundResultDisposition",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundResultDisposition(
    val value: Int,
    val description: String,
    val group: String,
    val terminal: Boolean
) {

    RECEIVED(0, "The refund notification was durably received before adjudication", "pending", false),

    REJECTED(1, "The refund notification failed verification or business matching", "rejected", true),

    SUCCESS_ACCEPTED(2, "A verified successful refund result was accepted", "accepted", true),

    FAILURE_ACCEPTED(3, "A verified failed refund result was accepted", "accepted", true),

    UNKNOWN_ACCEPTED(4, "A verified non-final refund result was accepted while budget remains reserved", "pending", true),

    ACCEPTED_DUPLICATE(5, "A duplicate notification repeated an already accepted payload", "accepted", true),

    REJECTED_DUPLICATE(6, "A duplicate notification repeated an already rejected payload", "rejected", true),

    CONFLICT(7, "A refund notification conflicts with an immutable prior result", "conflict", true),

    ATTEMPT_NOT_FOUND(8, "The referenced attempt does not belong to the refund", "rejected", true);

    companion object {
        private val enumMap: Map<Int, RefundResultDisposition> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundResultDisposition? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundResultDisposition, Int> {
        override fun convertToDatabaseColumn(attribute: RefundResultDisposition?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundResultDisposition? {
            return valueOfOrNull(dbData)
        }
    }
}
