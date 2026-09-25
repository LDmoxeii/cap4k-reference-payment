package com.only4.cap4k.reference.payment.domain.aggregates.refund.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "RefundStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.refund.enums",
    description = "退款单业务生命周期状态",
    aggregates = ["Refund"],
    family = "enum"
)
enum class RefundStatus(
    val value: Int,
    val description: String
) {

    REQUESTED(5, "退款申请已受理并占用预算，尚未创建渠道尝试"),

    PROCESSING(0, "退款预算已占用，渠道退款请求正在处理中"),

    SUCCEEDED(1, "已验证的渠道结果确认退款成功"),

    FAILED(2, "退款失败，已释放占用的支付退款预算"),

    RESULT_UNKNOWN(3, "可信的渠道结果尚未最终确定，退款预算继续占用"),

    REVIEW_REQUIRED(4, "已受理的退款请求超过复核阈值且仍无最终结果");

    companion object {
        private val enumMap: Map<Int, RefundStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): RefundStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<RefundStatus, Int> {
        override fun convertToDatabaseColumn(attribute: RefundStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): RefundStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
