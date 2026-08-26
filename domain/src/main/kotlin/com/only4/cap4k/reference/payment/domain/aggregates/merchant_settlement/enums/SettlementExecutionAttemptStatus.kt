package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementExecutionAttemptStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "结算划款尝试处理状态",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementExecutionAttemptStatus(
    val value: Int,
    val description: String
) {

    PROCESSING(0, "划款请求已受理，等待最终结果"),

    SUCCEEDED(1, "已接受通过验证的成功结果"),

    FAILED(2, "已接受通过验证的明确失败结果"),

    RESULT_UNKNOWN(3, "已接受通过验证的未知结果"),

    REVIEW_REQUIRED(4, "本次划款尝试需要经授权的人工复核"),

    CONFLICT_REVIEW_REQUIRED(5, "冲突证据需要经授权的人工复核");

    companion object {
        private val enumMap: Map<Int, SettlementExecutionAttemptStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementExecutionAttemptStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementExecutionAttemptStatus, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementExecutionAttemptStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementExecutionAttemptStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
