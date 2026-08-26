package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationBatchStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "对账批次业务生命周期状态",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationBatchStatus(
    val value: Int,
    val description: String
) {

    PENDING(0, "对账批次已创建，等待拉取对账单"),

    FETCHING(1, "正在拉取渠道对账单"),

    RECONCILING(2, "正在对账当前对账单修订版本"),

    AWAITING_DISPOSITION(3, "当前有效运行仍存在未解决差异"),

    COMPLETED(4, "当前有效运行已满足完成条件"),

    FETCH_FAILED(5, "渠道对账单拉取失败"),

    REVIEW_REQUIRED(6, "对账单时限或证据需要人工复核");

    companion object {
        private val enumMap: Map<Int, ReconciliationBatchStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationBatchStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationBatchStatus, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationBatchStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationBatchStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
