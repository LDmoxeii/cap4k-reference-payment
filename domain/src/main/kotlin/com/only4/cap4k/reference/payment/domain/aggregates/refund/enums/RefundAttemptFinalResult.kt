package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundAttemptFinalResult",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "退款渠道尝试最终结果",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundAttemptFinalResult(
    val value: Int,
    val description: String
) {

    SUCCESS(0, "渠道已返回并通过验证的退款成功结果"),

    FAILED(1, "渠道已返回并通过验证的退款失败结果"),

    GATEWAY_REJECTED(2, "网关在处理前拒绝了退款请求"),

    RETRYABLE_FAILURE(3, "本次渠道尝试失败但 policy 允许使用新 attempt 重试");

    companion object {
        private val enumMap: Map<Int, RefundAttemptFinalResult> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundAttemptFinalResult? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundAttemptFinalResult, Int> {
        override fun convertToDatabaseColumn(attribute: RefundAttemptFinalResult?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundAttemptFinalResult? {
            return valueOfOrNull(dbData)
        }
    }
}
