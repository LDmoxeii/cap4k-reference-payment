package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationBatchStatus",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationBatchStatus(
    val value: Int,
    val description: String
) {

    PENDING(0, "Batch exists and awaits statement retrieval"),

    FETCHING(1, "Channel statement retrieval is in progress"),

    RECONCILING(2, "A statement revision is being reconciled"),

    AWAITING_DISPOSITION(3, "Current run has unresolved differences"),

    COMPLETED(4, "Current effective run satisfies completion conditions"),

    FETCH_FAILED(5, "Statement retrieval failed"),

    REVIEW_REQUIRED(6, "The statement deadline or evidence requires operator review");

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
