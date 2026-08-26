package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentNotificationIntentState",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "商户支付成功通知意图状态",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentNotificationIntentState(
    val value: Int,
    val description: String
) {

    READY(0, "稳定的商户成功通知意图可由后续传输机制投递"),

    HELD_FOR_REVIEW(1, "通知意图已存在，但复核未解决前不得投递"),

    CANCELLED(2, "通知意图不得投递");

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
