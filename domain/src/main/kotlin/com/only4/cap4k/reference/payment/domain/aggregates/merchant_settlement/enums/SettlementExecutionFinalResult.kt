package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementExecutionFinalResult",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "结算划款尝试最终结果",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementExecutionFinalResult(
    val value: Int,
    val description: String
) {

    SUCCESS(0, "外部结算划款已成功完成"),

    FAILED(1, "外部结算划款已明确失败"),

    GATEWAY_REJECTED(2, "划款请求在处理前被网关拒绝"),

    UNKNOWN(3, "外部结算划款结果未知");

    companion object {
        private val enumMap: Map<Int, SettlementExecutionFinalResult> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementExecutionFinalResult? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementExecutionFinalResult, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementExecutionFinalResult?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementExecutionFinalResult? {
            return valueOfOrNull(dbData)
        }
    }
}
