package com.only4.cap4k.reference.payment.domain.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import java.math.BigDecimal
import java.math.RoundingMode

@DesignBlockMetadata(
    tag = "value_object",
    name = "Money",
    packageName = "com.only4.cap4k.reference.payment.domain.values",
    description = "Exact monetary amount paired with an ISO-style currency code",
    aggregates = [],
    family = "value-object"
)
@ConsistentCopyVisibility
data class Money private constructor(
    val amount: BigDecimal,
    val currency: String,
) {
    companion object {
        fun of(amount: BigDecimal, currency: String): Money {
            val normalizedCurrency = currency.trim().uppercase()
            require(normalizedCurrency in SUPPORTED_FRACTION_DIGITS) {
                "unsupported payment currency: $normalizedCurrency"
            }
            require(amount > BigDecimal.ZERO) { "payment amount must be positive" }
            require(amount.scale() <= fractionDigits(normalizedCurrency)) {
                "payment amount exceeds the supported precision for $normalizedCurrency"
            }
            return Money(
                amount = amount.setScale(fractionDigits(normalizedCurrency), RoundingMode.UNNECESSARY),
                currency = normalizedCurrency,
            )
        }

        private val SUPPORTED_FRACTION_DIGITS = mapOf(
            "CNY" to 2,
        )

        private fun fractionDigits(currency: String): Int =
            requireNotNull(SUPPORTED_FRACTION_DIGITS[currency]) { "unsupported payment currency: $currency" }
    }
}
