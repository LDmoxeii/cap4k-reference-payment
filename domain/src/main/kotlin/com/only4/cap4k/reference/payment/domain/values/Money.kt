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
    /**
     * 金额
     */
    val amount: BigDecimal,
    /**
     * 币种
     */
    val currency: String,
) {
    companion object {
        fun of(amount: BigDecimal, currency: String): Money {
            val normalizedCurrency = currency.trim().uppercase()
            require(normalizedCurrency in SUPPORTED_FRACTION_DIGITS) {
                "unsupported payment currency: $normalizedCurrency"
            }
            return of(amount, normalizedCurrency, fractionDigits(normalizedCurrency))
        }

        /**
         * The reference-policy boundary supplies the effective precision.  Aggregate state still
         * stores an exact Money value; this overload does not introduce mutable global currency
         * configuration into the domain model.
         */
        fun of(amount: BigDecimal, currency: String, currencyPrecision: Int): Money {
            val normalizedCurrency = currency.trim().uppercase()
            require(currencyPrecision >= 0) { "currency precision must not be negative" }
            require(amount > BigDecimal.ZERO) { "支付金额必须大于零" }
            require(amount.scale() <= currencyPrecision) {
                "payment amount exceeds the supported precision for $normalizedCurrency"
            }
            return Money(
                amount = amount.setScale(currencyPrecision, RoundingMode.UNNECESSARY),
                currency = normalizedCurrency,
            )
        }

        private val SUPPORTED_FRACTION_DIGITS = mapOf(
            "CNY" to 2,
        )

        fun fractionDigits(currency: String): Int {
            val normalizedCurrency = currency.trim().uppercase()
            return requireNotNull(SUPPORTED_FRACTION_DIGITS[normalizedCurrency]) {
                "unsupported payment currency: $normalizedCurrency"
            }
        }
    }
}
