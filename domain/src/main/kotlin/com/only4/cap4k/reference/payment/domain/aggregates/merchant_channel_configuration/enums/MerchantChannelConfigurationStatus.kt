package com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "MerchantChannelConfigurationStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums",
    description = "商户渠道配置生命周期状态",
    aggregates = ["MerchantChannelConfiguration"],
    family = "enum"
)
enum class MerchantChannelConfigurationStatus(
    val value: Int,
    val description: String
) {

    ACTIVE(0, "商户渠道配置可参与路由"),

    RETIRED(1, "商户渠道配置已停用，不再参与路由");

    companion object {
        private val enumMap: Map<Int, MerchantChannelConfigurationStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): MerchantChannelConfigurationStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<MerchantChannelConfigurationStatus, Int> {
        override fun convertToDatabaseColumn(attribute: MerchantChannelConfigurationStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): MerchantChannelConfigurationStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
