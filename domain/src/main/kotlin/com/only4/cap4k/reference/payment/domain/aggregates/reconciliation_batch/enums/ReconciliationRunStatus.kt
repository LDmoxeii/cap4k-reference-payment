package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationRunStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationRunStatus(
    val value: Int,
    val description: String
) {

    FETCHING(0, "Statement evidence is being acquired"),

    RECONCILING(1, "Platform and channel facts are being matched"),

    COMPLETED(2, "The run produced a complete immutable result"),

    FAILED(3, "The run failed and retained diagnostics"),

    SUPERSEDED(4, "A later revision is the current effective run");

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
