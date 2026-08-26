package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentReviewDecisionType",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "支付复核决定类型",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentReviewDecisionType(
    val value: Int,
    val description: String
) {

    SYSTEM_ACCEPT_SUCCESS(0, "确定性结果将未知复核裁定为成功"),

    SYSTEM_CONFIRM_FAILURE(1, "确定性结果将未知复核裁定为失败"),

    ACCEPT_LATE_SUCCESS(2, "经授权的操作人接受了可信的迟到成功结果"),

    CONFIRM_FAILURE(3, "经授权的操作人确认未知结果为失败"),

    KEEP_CURRENT_TERMINAL(4, "经授权的操作人保留现有关闭或失败终态"),

    KEEP_ACCEPTED_SUCCESS_WITH_REMEDIATION(5, "经授权的操作人保留首个成功结果并记录补救措施");

    companion object {
        private val enumMap: Map<Int, PaymentReviewDecisionType> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentReviewDecisionType? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentReviewDecisionType, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentReviewDecisionType?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentReviewDecisionType? {
            return valueOfOrNull(dbData)
        }
    }
}
