package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(
    tag = "enum",
    name = "SettlementResultDisposition",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums",
    description = "",
    aggregates = ["MerchantSettlement"],
    family = "enum"
)
enum class SettlementResultDisposition(
    val value: Int,
    val description: String,
    val group: String,
    val terminal: Boolean
) {

    RECEIVED(0, "The result notification was durably received", "pending", false),

    REJECTED(1, "The notification failed verification or business matching", "rejected", true),

    SUCCESS_ACCEPTED(2, "A verified successful settlement result was accepted", "accepted", true),

    FAILURE_ACCEPTED(3, "A verified failed settlement result was accepted", "accepted", true),

    UNKNOWN_ACCEPTED(4, "A verified unknown settlement result was accepted", "review", true),

    ACCEPTED_DUPLICATE(5, "A duplicate notification repeated accepted facts", "accepted", true),

    REJECTED_DUPLICATE(6, "A duplicate notification repeated rejected facts", "rejected", true),

    CONFLICT(7, "A notification identity was reused with conflicting facts", "conflict", true),

    ATTEMPT_NOT_FOUND(8, "The referenced attempt does not belong to the settlement", "rejected", true);

    companion object {
        private val enumMap: Map<Int, SettlementResultDisposition> = entries.associateBy { it.value }

        fun valueOfOrNull(value: Int?): SettlementResultDisposition? = enumMap[value]
    }

    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<SettlementResultDisposition, Int> {
        override fun convertToDatabaseColumn(attribute: SettlementResultDisposition?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): SettlementResultDisposition? {
            return valueOfOrNull(dbData)
        }
    }
}
