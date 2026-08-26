package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationDifferenceType",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "对账差异类型",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationDifferenceType(
    val value: Int,
    val description: String
) {

    MATCHED(0, "平台证据与渠道证据匹配"),

    PLATFORM_ONLY(1, "仅存在平台事实"),

    CHANNEL_ONLY(2, "仅存在渠道记录"),

    AMOUNT_MISMATCH(3, "稳定身份匹配，但金额不一致"),

    CURRENCY_MISMATCH(4, "稳定身份匹配，但币种不一致"),

    STATUS_MISMATCH(5, "稳定身份匹配，但最终状态不一致"),

    DUPLICATE_CHANNEL_RECORD(6, "对账单重复包含同一稳定渠道记录"),

    UNMATCHED(7, "无法建立经认可的稳定关联");

    companion object {
        private val enumMap: Map<Int, ReconciliationDifferenceType> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationDifferenceType? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationDifferenceType, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationDifferenceType?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationDifferenceType? {
            return valueOfOrNull(dbData)
        }
    }
}
