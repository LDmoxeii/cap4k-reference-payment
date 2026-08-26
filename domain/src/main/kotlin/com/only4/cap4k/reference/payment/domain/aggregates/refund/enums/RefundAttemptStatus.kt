package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundAttemptStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "退款渠道尝试处理状态",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundAttemptStatus(
    val value: Int,
    val description: String
) {

    PROCESSING(0, "渠道退款尝试正在处理中"),

    SUCCEEDED(1, "渠道退款尝试已成功完成"),

    FAILED(2, "渠道退款尝试已失败完成"),

    RESULT_UNKNOWN(3, "渠道返回了可信但尚未最终确定的结果"),

    REVIEW_REQUIRED(4, "渠道退款尝试超过配置阈值，需要复核");

    companion object {
        private val enumMap: Map<Int, RefundAttemptStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundAttemptStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundAttemptStatus, Int> {
        override fun convertToDatabaseColumn(attribute: RefundAttemptStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundAttemptStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
