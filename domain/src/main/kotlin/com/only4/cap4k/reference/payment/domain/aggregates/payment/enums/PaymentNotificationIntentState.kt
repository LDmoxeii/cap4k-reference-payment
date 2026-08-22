package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentNotificationIntentState",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentNotificationIntentState(
    val value: Int,
    val description: String
) {

    READY(0, "The stable merchant-success intent may be delivered by a future transport"),

    HELD_FOR_REVIEW(1, "The intent exists but cannot be delivered while review is unresolved"),

    CANCELLED(2, "The intent must not be delivered");

    companion object {
        private val enumMap: Map<Int, PaymentNotificationIntentState> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentNotificationIntentState? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentNotificationIntentState, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentNotificationIntentState?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentNotificationIntentState? {
            return valueOfOrNull(dbData)
        }
    }
}
