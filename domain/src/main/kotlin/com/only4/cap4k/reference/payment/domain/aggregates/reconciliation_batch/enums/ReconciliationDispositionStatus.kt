package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationDispositionStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "对账差异处置执行状态",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationDispositionStatus(
    val value: Int,
    val description: String
) {

    REJECTED(0, "处置尝试已被拒绝"),

    APPLIED(1, "已追加经授权的处置记录");

    companion object {
        private val enumMap: Map<Int, ReconciliationDispositionStatus> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationDispositionStatus? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationDispositionStatus, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationDispositionStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationDispositionStatus? {
            return valueOfOrNull(dbData)
        }
    }
}
