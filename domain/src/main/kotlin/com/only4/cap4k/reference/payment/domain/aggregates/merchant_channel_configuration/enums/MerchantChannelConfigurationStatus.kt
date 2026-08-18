package com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "MerchantChannelConfigurationStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums",
    description = "",
    aggregates = ["MerchantChannelConfiguration"],
    family = "enum"
)
enum class MerchantChannelConfigurationStatus(
    val value: Int,
    val description: String
) {

    ACTIVE(0, "The merchant channel configuration is eligible for routing"),

    RETIRED(1, "The merchant channel configuration is no longer eligible");

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
