package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "PaymentReviewSettlementImpact",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums",
    description = "支付复核对结算资格的影响",
    aggregates = ["Payment"],
    family = "enum"
)
enum class PaymentReviewSettlementImpact(
    val value: Int,
    val description: String
) {

    BLOCKS_SETTLEMENT(0, "复核案件阻断自动对账和结算资格"),

    ALLOWS_SETTLEMENT(1, "复核案件不再阻断自动结算资格");

    companion object {
        private val enumMap: Map<Int, PaymentReviewSettlementImpact> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): PaymentReviewSettlementImpact? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentReviewSettlementImpact, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentReviewSettlementImpact?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentReviewSettlementImpact? {
            return valueOfOrNull(dbData)
        }
    }
}
