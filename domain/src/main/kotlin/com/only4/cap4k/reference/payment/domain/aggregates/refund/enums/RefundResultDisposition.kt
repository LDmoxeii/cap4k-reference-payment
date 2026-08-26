package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundResultDisposition",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "退款渠道结果通知处置结果",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundResultDisposition(
    val value: Int,
    val description: String,
    val group: String,
    val terminal: Boolean
) {

    RECEIVED(0, "退款通知已持久化接收，等待裁定", "pending", false),

    REJECTED(1, "退款通知未通过验证或业务匹配", "rejected", true),

    SUCCESS_ACCEPTED(2, "已接受通过验证的退款成功结果", "accepted", true),

    FAILURE_ACCEPTED(3, "已接受通过验证的退款失败结果", "accepted", true),

    UNKNOWN_ACCEPTED(4, "已接受通过验证但尚未最终确定的退款结果，退款预算继续占用", "pending", true),

    ACCEPTED_DUPLICATE(5, "重复通知重放了已接受的载荷", "accepted", true),

    REJECTED_DUPLICATE(6, "重复通知重放了已拒绝的载荷", "rejected", true),

    CONFLICT(7, "退款通知与不可变的既有结果发生冲突", "conflict", true),

    ATTEMPT_NOT_FOUND(8, "引用的退款尝试不属于当前退款单", "rejected", true);

    companion object {
        private val enumMap: Map<Int, RefundResultDisposition> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundResultDisposition? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundResultDisposition, Int> {
        override fun convertToDatabaseColumn(attribute: RefundResultDisposition?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundResultDisposition? {
            return valueOfOrNull(dbData)
        }
    }
}
