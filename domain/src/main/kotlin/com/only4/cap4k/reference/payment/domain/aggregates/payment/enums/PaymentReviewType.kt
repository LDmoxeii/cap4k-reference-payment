package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentReviewType",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "支付复核类型",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentReviewType(
    val value: Int,
    val description: String
) {

    EXPIRY_RESULT_UNKNOWN(0, "已过期的处理中支付尝试需要结果复核"),

    LATE_SUCCESS_AFTER_TERMINAL(1, "支付已关闭或失败后收到可信的迟到成功结果"),

    MULTIPLE_ATTEMPT_SUCCESS(2, "多个支付尝试包含可信的成功证据"),

    SUCCESS_AFTER_FAILURE_CONFLICT(3, "已接受失败结果后又收到成功证据"),

    FAILURE_OR_UNKNOWN_AFTER_SUCCESS(4, "已接受成功结果后又收到失败或未知证据"),

    NOTIFICATION_PAYLOAD_CONFLICT(5, "同一通知身份被用于不同载荷"),

    MERCHANT_ORDER_SUCCESS_CONFLICT(6, "另一支付单已占有该商户订单的已接受成功声明"),

    CONCURRENT_ATTEMPT_RISK(7, "存在 in-flight 或 unknown 尝试时以显式风险说明创建新尝试");

    companion object {
        private val enumMap: Map<Int, PaymentReviewType> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentReviewType? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentReviewType, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentReviewType?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentReviewType? {
            return valueOfOrNull(dbData)
        }
    }
}
