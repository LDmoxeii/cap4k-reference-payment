package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(tag = "enum", name = "PaymentAttemptStatus", packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums", description = "支付渠道尝试处理状态", aggregates = ["Payment"], family = "enum")
enum class PaymentAttemptStatus(val value: Int, val description: String) {
    CREATED(0, "支付尝试已创建，尚未调用渠道"),
    SUBMITTED(1, "稳定提交身份已冻结并已调用渠道，等待受理结果"),
    ACCEPTED(2, "渠道已受理付款请求，尚未形成资金成功事实"),
    RESULT_UNKNOWN(3, "渠道提交或结果未知，需要收敛或人工核对"),
    SUCCEEDED(4, "渠道已返回并通过验证的成功结果"),
    FAILED(5, "渠道已返回并通过验证的失败结果"),
    REJECTED(6, "渠道在提交阶段明确拒绝请求");

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
