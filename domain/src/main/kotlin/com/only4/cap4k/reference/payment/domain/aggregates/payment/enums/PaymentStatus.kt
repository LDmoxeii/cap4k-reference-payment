package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "支付单业务生命周期状态",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentStatus(
    val value: Int,
    val description: String
) {

    PENDING(0, "支付已受理，但尚无有效支付尝试完成"),

    PROCESSING(1, "至少有一次支付尝试正在处理中"),

    SUCCEEDED(2, "已验证的渠道结果确认支付成功"),

    FAILED(3, "支付已形成可接受的失败结果"),

    CLOSED(4, "支付已关闭且未成功"),

    RESULT_UNKNOWN(5, "最终结果仍需通过对账确认");

    companion object {
        private val enumMap: Map<Int, PaymentStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentStatus, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
