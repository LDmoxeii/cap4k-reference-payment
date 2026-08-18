package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ChannelResultDisposition",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class ChannelResultDisposition(
    val value: Int,
    val description: String,
    val group: String,
    val terminal: Boolean
) {

    RECEIVED(0, "The channel notification was durably received before adjudication", "pending", false),

    REJECTED(1, "The channel notification failed verification or business matching", "rejected", true),

    SUCCESS_ACCEPTED(2, "A verified successful result was accepted", "accepted", true),

    FAILURE_ACCEPTED(3, "A verified failed result was accepted", "accepted", true),

    ACCEPTED_DUPLICATE(4, "A duplicate notification repeated an already accepted payload", "accepted", true),

    REJECTED_DUPLICATE(5, "A duplicate notification repeated an already rejected payload", "rejected", true),

    CONFLICT(6, "A notification identity was reused with conflicting facts", "conflict", true),

    ATTEMPT_NOT_FOUND(7, "The referenced attempt does not belong to the payment", "rejected", true);

    fun isAccepted(): Boolean = group == "accepted"

    fun isRejected(): Boolean = group == "rejected"

    fun isDuplicate(): Boolean =
        this == ACCEPTED_DUPLICATE || this == REJECTED_DUPLICATE

    fun isConflicting(): Boolean = group == "conflict"

    fun isTerminal(): Boolean = terminal

    companion object {
        private val enumMap: Map<Int, ChannelResultDisposition> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ChannelResultDisposition? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ChannelResultDisposition, Int> {
        override fun convertToDatabaseColumn(attribute: ChannelResultDisposition?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ChannelResultDisposition? {
            return valueOfOrNull(dbData)
        }
    }
}
