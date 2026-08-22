package com.only4.cap4k.reference.payment.domain.aggregates.payment.enums

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import jakarta.persistence.AttributeConverter

@DesignBlockMetadata(tag = "enum", name = "PaymentAttemptFinalResult", packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.enums", description = "", aggregates = ["Payment"], family = "enum")
enum class PaymentAttemptFinalResult(val value: Int, val description: String) {
    SUCCESS(0, "The channel reported a verified successful result"),
    FAILED(1, "The channel reported a verified failed result"),
    GATEWAY_REJECTED(2, "The gateway rejected the attempt before a channel result"),
    RESULT_UNKNOWN(3, "The attempt expired or the channel reported an unknown result");

    companion object {
        private val enumMap = entries.associateBy { it.value }
        fun valueOfOrNull(value: Int?): PaymentAttemptFinalResult? = enumMap[value]
    }
    @jakarta.persistence.Converter(autoApply = false)
    class Converter : AttributeConverter<PaymentAttemptFinalResult, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentAttemptFinalResult?): Int? = attribute?.value
        override fun convertToEntityAttribute(dbData: Int?): PaymentAttemptFinalResult? = valueOfOrNull(dbData)
    }
}
