package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationRunStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "对账运行处理状态",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationRunStatus(
    val value: Int,
    val description: String
) {

    FETCHING(0, "正在获取对账单证据"),

    RECONCILING(1, "正在匹配平台事实与渠道事实"),

    COMPLETED(2, "本次运行已生成完整且不可变的结果"),

    FAILED(3, "本次运行失败并保留了诊断信息"),

    SUPERSEDED(4, "后续修订版本已成为当前有效运行");

    companion object {
        private val enumMap: Map<Int, ReconciliationRunStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationRunStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationRunStatus, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationRunStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationRunStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
