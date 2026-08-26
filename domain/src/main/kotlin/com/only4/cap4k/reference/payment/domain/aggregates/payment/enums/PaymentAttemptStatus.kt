package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(tag = "enum", name = "PaymentAttemptStatus", packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums", description = "支付渠道尝试处理状态", aggregates = ["Payment"], family = "enum")
enum class PaymentAttemptStatus(val value: Int, val description: String) {
    PROCESSING(0, "渠道支付尝试正在处理中"),
    SUCCEEDED(1, "渠道支付尝试已成功完成"),
    FAILED(2, "渠道支付尝试已失败完成"),
    RESULT_UNKNOWN(3, "渠道支付尝试尚无最终结果，需要复核");

    companion object {
        private val enumMap = entries.associateBy { it.value }
        fun valueOfOrNull(value: Int?): PaymentAttemptStatus? = enumMap[value]
    }
    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentAttemptStatus, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentAttemptStatus?): Int? = attribute?.value
        override fun convertToEntityAttribute(dbData: Int?): PaymentAttemptStatus? = valueOfOrNull(dbData)
    }
}
