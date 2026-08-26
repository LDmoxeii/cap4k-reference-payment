package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementResultDisposition",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "结算划款结果通知处置结果",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementResultDisposition(
    val value: Int,
    val description: String,
    val group: String,
    val terminal: Boolean
) {

    RECEIVED(0, "结果通知已持久化接收", "pending", false),

    REJECTED(1, "通知未通过验证或业务匹配", "rejected", true),

    SUCCESS_ACCEPTED(2, "已接受通过验证的结算成功结果", "accepted", true),

    FAILURE_ACCEPTED(3, "已接受通过验证的结算失败结果", "accepted", true),

    UNKNOWN_ACCEPTED(4, "已接受通过验证的结算未知结果", "review", true),

    ACCEPTED_DUPLICATE(5, "重复通知重放了已接受的事实", "accepted", true),

    REJECTED_DUPLICATE(6, "重复通知重放了已拒绝的事实", "rejected", true),

    CONFLICT(7, "同一通知身份被重复使用且事实发生冲突", "conflict", true),

    ATTEMPT_NOT_FOUND(8, "引用的划款尝试不属于当前结算单", "rejected", true);

    companion object {
        private val enumMap: Map<Int, SettlementResultDisposition> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementResultDisposition? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementResultDisposition, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementResultDisposition?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementResultDisposition? {
            return valueOfOrNull(dbData)
        }
    }
}
