package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "ReconciliationTransactionKind",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class ReconciliationTransactionKind(
    val value: Int,
    val description: String
) {

    PAYMENT(0, "Payment funds fact"),

    REFUND(1, "Refund funds fact");

    companion object {
        private val enumMap: Map<Int, ReconciliationTransactionKind> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): ReconciliationTransactionKind? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<ReconciliationTransactionKind, Int> {
        override fun convertToDatabaseColumn(attribute: ReconciliationTransactionKind?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReconciliationTransactionKind? {
            return valueOfOrNull(dbData)
        }
    }
}
