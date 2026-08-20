package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "DispositionAuthorization",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums",
    description = "",
    aggregates = ["ReconciliationBatch"],
    family = "enum"
)
enum class DispositionAuthorization(
    val value: Int,
    val description: String
) {

    DENIED(0, "Operator was not authorized and the attempt is retained"),

    AUTHORIZED(1, "Operator was authorized to append a disposition");

    companion object {
        private val enumMap: Map<Int, DispositionAuthorization> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): DispositionAuthorization? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<DispositionAuthorization, Int> {
        override fun convertToDatabaseColumn(attribute: DispositionAuthorization?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): DispositionAuthorization? {
            return valueOfOrNull(dbData)
        }
    }
}
