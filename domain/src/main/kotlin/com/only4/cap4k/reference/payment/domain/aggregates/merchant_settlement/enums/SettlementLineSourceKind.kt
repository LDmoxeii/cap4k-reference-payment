package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementLineSourceKind",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "结算明细来源类型",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementLineSourceKind(
    val value: Int,
    val description: String
) {

    PAYMENT(0, "已验证的支付成功事实"),

    REFUND(1, "已验证的退款成功事实"),

    RECONCILIATION_CONFIRMATION(2, "经授权的对账确认事实"),

    ADJUSTMENT(3, "可追溯的结算调整事实");

    companion object {
        private val enumMap: Map<Int, SettlementLineSourceKind> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementLineSourceKind? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementLineSourceKind, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementLineSourceKind?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementLineSourceKind? {
            return valueOfOrNull(dbData)
        }
    }
}
