package com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "MerchantNotificationDeliveryOutcome",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums",
    description = "商户通知单次投递结果",
    aggregates = ["MerchantNotification"],
    family = "enum",
)
enum class MerchantNotificationDeliveryOutcome(val value: Int, val description: String) {
    SUCCESS(0, "发送方确认投递成功"),
    FAILURE(1, "发送方明确投递失败"),
    RESULT_UNKNOWN(2, "发送方结果未知，不可假定失败或成功");

    companion object {
        private val enumMap = entries.associateBy { it.value }
        fun valueOfOrNull(value: Int?): MerchantNotificationDeliveryOutcome? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<MerchantNotificationDeliveryOutcome, Int> {
        override fun convertToDatabaseColumn(attribute: MerchantNotificationDeliveryOutcome?): Int? = attribute?.value
        override fun convertToEntityAttribute(dbData: Int?): MerchantNotificationDeliveryOutcome? = valueOfOrNull(dbData)
    }
}
