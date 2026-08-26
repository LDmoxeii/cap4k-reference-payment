package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationDispositionConclusion",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "对账差异处置结论",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationDispositionConclusion(
    val value: Int,
    val description: String
) {

    ACCEPT_AS_MATCHED(0, "现有证据足以确认可接受的匹配"),

    CONFIRM_PLATFORM_FACT(1, "渠道证据授权新增平台确认事实"),

    ACCEPT_CHANNEL_FACT(2, "已基于证据接受仅渠道侧存在的事实"),

    NO_SETTLEMENT_IMPACT(3, "已记录该差异不影响结算"),

    ESCALATE(4, "差异保持未解决，等待后续跟进");

    companion object {
        private val enumMap: Map<Int, ReconciliationDispositionConclusion> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationDispositionConclusion? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationDispositionConclusion, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationDispositionConclusion?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationDispositionConclusion? {
            return valueOfOrNull(dbData)
        }
    }
}
