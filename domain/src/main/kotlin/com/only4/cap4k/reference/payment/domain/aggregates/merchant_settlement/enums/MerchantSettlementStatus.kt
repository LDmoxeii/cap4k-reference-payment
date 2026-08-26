package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "MerchantSettlementStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "商户结算单业务生命周期状态",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class MerchantSettlementStatus(
    val value: Int,
    val description: String
) {

    PREPARING(0, "正在准备结算构成"),

    REVIEW_REQUIRED(1, "已准备的证据需要人工复核"),

    PREPARED(2, "结算构成和汇总金额已就绪，等待确认"),

    CONFIRMED(3, "结算构成已冻结，可以执行划款"),

    PROCESSING(4, "外部划款请求已受理"),

    SUCCEEDED(5, "已验证的划款结果确认结算成功"),

    FAILED(6, "最近一次划款尝试已明确失败"),

    RESULT_UNKNOWN(7, "划款结果可信但尚未最终确定"),

    NEGATIVE_REVIEW_REQUIRED(8, "净额为负，禁止发起对外划款"),

    VOIDED(9, "结算单在对外执行前已作废"),

    CONFLICT_REVIEW_REQUIRED(10, "划款证据发生冲突，需要人工复核");

    companion object {
        private val enumMap: Map<Int, MerchantSettlementStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): MerchantSettlementStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<MerchantSettlementStatus, Int> {
        override fun convertToDatabaseColumn(attribute: MerchantSettlementStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): MerchantSettlementStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
