package com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "MerchantNotificationStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums",
    description = "商户通知投递状态",
    aggregates = ["MerchantNotification"],
    family = "enum",
)
enum class MerchantNotificationStatus(val value: Int, val description: String) {
    PENDING(0, "通知意图已形成，尚无最终投递结果"),
    DELIVERED(1, "通知已成功投递，不再重试"),
    FAILED(2, "最近一次投递明确失败，可按冻结策略重试"),
    RESULT_UNKNOWN(3, "最近一次投递结果未知，不自动重试");

    companion object {
        private val enumMap = entries.associateBy { it.value }
        fun valueOfOrNull(value: Int?): MerchantNotificationStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<MerchantNotificationStatus, Int> {
        override fun convertToDatabaseColumn(attribute: MerchantNotificationStatus?): Int? = attribute?.value
        override fun convertToEntityAttribute(dbData: Int?): MerchantNotificationStatus? = valueOfOrNull(dbData)
    }
}
