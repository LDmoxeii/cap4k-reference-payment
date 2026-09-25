package com.only4.cap4k.reference.payment.adapter.endpoints

import com.only4.cap4k.reference.payment.contract.common.Money as ContractMoney
import java.math.BigDecimal
import java.util.Currency

/** Converts the reference wire-format minor-unit value without using binary floating point. */
internal fun ContractMoney.toDomainAmount(): BigDecimal {
    require(currency.trim().matches(Regex("[A-Za-z]{3}"))) { "currency must be an ISO 4217 code" }
    val normalizedCurrency = currency.trim().uppercase()
    require(currency == normalizedCurrency) { "currency must be an uppercase ISO 4217 code" }
    require(amountMinor.matches(Regex("-?[0-9]+"))) { "amountMinor must be a decimal integer string" }
    return BigDecimal(amountMinor).movePointLeft(externalCurrencyFractionDigits(normalizedCurrency))
}

/** Projects an exact decimal domain amount into the mandatory minor-unit wire representation. */
internal fun BigDecimal.toContractMoney(currency: String): ContractMoney {
    val normalizedCurrency = currency.trim().uppercase()
    val fractionDigits = externalCurrencyFractionDigits(normalizedCurrency)
    val normalizedAmount = setScale(fractionDigits, java.math.RoundingMode.UNNECESSARY)
    return ContractMoney(
        currency = normalizedCurrency,
        amountMinor = normalizedAmount.movePointRight(fractionDigits).toBigIntegerExact().toString(),
    )
}

private fun externalCurrencyFractionDigits(currency: String): Int {
    val fractionDigits = runCatching { Currency.getInstance(currency).defaultFractionDigits }
        .getOrElse { throw IllegalArgumentException("currency must be an ISO 4217 code") }
    require(fractionDigits >= 0) { "currency must define a minor-unit precision" }
    return fractionDigits
}
