package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(tag = "enum", name = "PaymentAttemptFinalResult", packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums", description = "支付渠道尝试最终结果", aggregates = ["Payment"], family = "enum")
enum class PaymentAttemptFinalResult(val value: Int, val description: String) {
    SUCCESS(0, "渠道已返回并通过验证的成功结果"),
    FAILED(1, "渠道已返回并通过验证的失败结果"),
    GATEWAY_REJECTED(2, "网关在获得渠道结果前拒绝了本次尝试"),
    RESULT_UNKNOWN(3, "渠道支付尝试已过期，且没有可信的最终结果");

    companion object {
        private val enumMap = entries.associateBy { it.value }
        fun valueOfOrNull(value: Int?): PaymentAttemptFinalResult? = enumMap[value]
    }
    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentAttemptFinalResult, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentAttemptFinalResult?): Int? = attribute?.value
        override fun convertToEntityAttribute(dbData: Int?): PaymentAttemptFinalResult? = valueOfOrNull(dbData)
    }
}
