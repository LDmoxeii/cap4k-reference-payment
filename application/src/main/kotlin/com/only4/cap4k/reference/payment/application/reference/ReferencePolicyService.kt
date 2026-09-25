package com.only4.cap4k.reference.payment.application.reference

import com.only4.cap4k.reference.payment.domain.aggregates.payment.SettlementFeeRule
import com.only4.cap4k.reference.payment.domain.values.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Period
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Service

/**
 * The deterministic policy source used by the reference profile.  It deliberately lives outside
 * aggregates: the policy decides facts *when they are created*, while the aggregate persists the
 * resulting snapshot and never consults this mutable reference control state again.
 */
@Service
class ReferencePolicyService {
    private val state = AtomicReference(DEFAULT)

    fun current(): ReferencePolicy = state.get()

    /** Resolves a collection request against one atomic policy snapshot. */
    fun pageSize(requested: Int?): Int {
        val policy = current()
        return (requested ?: policy.defaultPageSize).also {
            require(it in 1..policy.maxPageSize) {
                "pageSize 必须在 1 到 ${policy.maxPageSize} 之间"
            }
        }
    }

    @Synchronized
    fun configure(override: ReferencePolicyOverride): ReferencePolicy {
        val before = state.get()
        val businessTimezone = override.businessTimezone
            ?.trim()
            ?.also { require(it.isNotEmpty()) { "businessTimezone 不能为空" } }
            ?.also { runCatching { ZoneId.of(it) }.getOrElse { throw IllegalArgumentException("非法 businessTimezone：$it") } }
            ?: before.businessTimezone
        val currencies = override.enabledCurrencies?.let(::normalizeCurrencies) ?: before.enabledCurrencies
        val precisions = override.currencyPrecisions?.let(::normalizePrecisions) ?: before.currencyPrecisions
        require(currencies.all { precisions.containsKey(it) }) {
            "每个启用币种必须有明确的 currency precision"
        }
        // Preserve the effective decimal rate itself in the payment success fact.  The legacy
        // basis-point projection is retained for the existing settlement line model, so this
        // reference profile accepts rates representable in whole basis points.  Normalize first:
        // JSON values such as 0.00600 are semantically the documented 0.006 default.
        val feeRate = (override.feeRate ?: before.feeRate).stripTrailingZeros()
        require(feeRate.signum() >= 0) { "feeRate 不能为负数" }
        require(feeRate.scale() <= 4) { "feeRate 仅支持精确到 1/10000，以保持基点快照可复核" }
        val feeBasisPoints = feeRate.movePointRight(4).intValueExact()
        val roundingMode = override.roundingMode?.let(::parseRoundingMode) ?: before.roundingMode
        val fixedFees = override.fixedFees?.let(::normalizeFixedFees) ?: before.fixedFees
        fixedFees.forEach { (currency, fixedFee) ->
            require(currency in currencies) { "固定费币种 $currency 未启用" }
            require(fixedFee.scale() <= requireNotNull(precisions[currency])) {
                "固定费 $currency 超过该币种精度"
            }
        }
        val paymentExpiry = positive(override.paymentExpiry ?: before.paymentExpiry, "paymentExpiry")
        val unknownResultReviewAfter = positive(
            override.unknownResultReviewAfter ?: before.unknownResultReviewAfter,
            "unknownResultReviewAfter",
        )
        val operationPollRetryAfterMs = override.operationPollRetryAfterMs ?: before.operationPollRetryAfterMs
        require(operationPollRetryAfterMs > 0) { "operationPollRetryAfterMs 必须为正数" }
        val operationObservationTimeout = positive(
            override.operationObservationTimeout ?: before.operationObservationTimeout,
            "operationObservationTimeout",
        )
        val refundWindow = positive(override.refundWindow ?: before.refundWindow, "refundWindow")
        val maxRefundAttempts = positive(override.maxRefundAttempts ?: before.maxRefundAttempts, "maxRefundAttempts")
        val billReadMaxAttempts = positive(
            override.billReadMaxAttempts ?: before.billReadMaxAttempts,
            "billReadMaxAttempts",
        )
        val billReadBackoff = positive(override.billReadBackoff ?: before.billReadBackoff, "billReadBackoff")
        val merchantNotificationMaxAttempts = positive(
            override.merchantNotificationMaxAttempts ?: before.merchantNotificationMaxAttempts,
            "merchantNotificationMaxAttempts",
        )
        val negativeSettlementPolicy = override.negativeSettlementPolicy
            ?.trim()
            ?.uppercase()
            ?: before.negativeSettlementPolicy
        require(negativeSettlementPolicy == "MANUAL_REVIEW_NO_AUTO_EXECUTION") {
            "negativeSettlementPolicy 仅支持 MANUAL_REVIEW_NO_AUTO_EXECUTION"
        }
        val largeRefundReviewThreshold = override.largeRefundReviewThreshold ?: before.largeRefundReviewThreshold
        require(largeRefundReviewThreshold == null || largeRefundReviewThreshold.signum() > 0) {
            "largeRefundReviewThreshold 必须为正数"
        }
        val defaultPageSize = positive(override.defaultPageSize ?: before.defaultPageSize, "defaultPageSize")
        val maxPageSize = positive(override.maxPageSize ?: before.maxPageSize, "maxPageSize")
        require(defaultPageSize <= maxPageSize) { "defaultPageSize 不能大于 maxPageSize" }
        return ReferencePolicy(
            businessTimezone = businessTimezone,
            enabledCurrencies = currencies,
            paymentExpiry = paymentExpiry,
            unknownResultReviewAfter = unknownResultReviewAfter,
            operationPollRetryAfterMs = operationPollRetryAfterMs,
            operationObservationTimeout = operationObservationTimeout,
            refundWindow = refundWindow,
            maxRefundAttempts = maxRefundAttempts,
            billReadMaxAttempts = billReadMaxAttempts,
            billReadBackoff = billReadBackoff,
            feeRate = feeRate,
            feeBasisPoints = feeBasisPoints,
            fixedFees = fixedFees,
            roundingMode = roundingMode,
            currencyPrecisions = precisions,
            merchantNotificationMaxAttempts = merchantNotificationMaxAttempts,
            negativeSettlementPolicy = negativeSettlementPolicy,
            largeRefundReviewThreshold = largeRefundReviewThreshold,
            defaultPageSize = defaultPageSize,
            maxPageSize = maxPageSize,
        ).also(state::set)
    }

    @Synchronized
    fun reset(): ReferencePolicy = DEFAULT.also(state::set)

    fun money(amount: BigDecimal, currency: String, effectivePolicy: ReferencePolicy = current()): Money {
        val normalizedCurrency = currency.trim().uppercase()
        require(normalizedCurrency in effectivePolicy.enabledCurrencies) { "币种 $normalizedCurrency 未在 ReferencePolicy 启用" }
        return Money.of(amount, normalizedCurrency, requireNotNull(effectivePolicy.currencyPrecisions[normalizedCurrency]))
    }

    /** Captures the effective policy used by the first accepted payment-success fact. */
    fun settlementFeeRule(currency: String): SettlementFeeRule {
        val policy = current()
        val normalizedCurrency = currency.trim().uppercase()
        require(normalizedCurrency in policy.enabledCurrencies) { "币种 $normalizedCurrency 未在 ReferencePolicy 启用" }
        return SettlementFeeRule(
            configurationId = "reference-policy",
            feeRate = policy.feeRate,
            basisPoints = policy.feeBasisPoints,
            fixedFeeAmount = policy.fixedFees[normalizedCurrency] ?: BigDecimal.ZERO,
            roundingMode = policy.roundingMode,
            currencyPrecision = requireNotNull(policy.currencyPrecisions[normalizedCurrency]),
        )
    }

    private fun normalizeCurrencies(currencies: Set<String>): Set<String> {
        require(currencies.isNotEmpty()) { "enabledCurrencies 不能为空" }
        return currencies.map { currency ->
            currency.trim().uppercase().also { require(CURRENCY.matches(it)) { "非法币种：$currency" } }
        }.toSortedSet()
    }

    private fun normalizePrecisions(precisions: Map<String, Int>): Map<String, Int> {
        require(precisions.isNotEmpty()) { "currencyPrecisions 不能为空" }
        return precisions.entries.associate { (currency, precision) ->
            val normalized = currency.trim().uppercase()
            require(CURRENCY.matches(normalized)) { "非法币种：$currency" }
            require(precision in 0..4) { "币种 $normalized 的 precision 必须在 0..4" }
            normalized to precision
        }.toSortedMap()
    }

    private fun normalizeFixedFees(fees: Map<String, BigDecimal>): Map<String, BigDecimal> = fees.entries.associate { (currency, fee) ->
        val normalized = currency.trim().uppercase()
        require(CURRENCY.matches(normalized)) { "非法币种：$currency" }
        require(fee.signum() >= 0) { "固定费不能为负数" }
        normalized to fee.stripTrailingZeros()
    }.toSortedMap()

    private fun parseRoundingMode(value: String): RoundingMode = runCatching {
        RoundingMode.valueOf(value.trim().uppercase())
    }.getOrElse { throw IllegalArgumentException("不支持的 roundingMode：$value") }

    private fun positive(value: Duration, name: String): Duration = value.also {
        require(!it.isZero && !it.isNegative) { "$name 必须为正 duration" }
    }

    private fun positive(value: Period, name: String): Period = value.also {
        require(!it.isZero && !it.isNegative) { "$name 必须为正 period" }
    }

    private fun positive(value: Int, name: String): Int = value.also {
        require(it > 0) { "$name 必须为正数" }
    }

    companion object {
        private val CURRENCY = Regex("[A-Z]{3}")

        val DEFAULT = ReferencePolicy(
            businessTimezone = "Asia/Shanghai",
            enabledCurrencies = sortedSetOf("CNY"),
            paymentExpiry = Duration.ofMinutes(30),
            unknownResultReviewAfter = Duration.ofMinutes(5),
            operationPollRetryAfterMs = 100,
            operationObservationTimeout = Duration.ofSeconds(30),
            refundWindow = Period.ofDays(30),
            maxRefundAttempts = 2,
            billReadMaxAttempts = 3,
            billReadBackoff = Duration.ofMinutes(1),
            feeRate = BigDecimal("0.006"),
            feeBasisPoints = 60,
            fixedFees = sortedMapOf("CNY" to BigDecimal.ZERO),
            roundingMode = RoundingMode.HALF_UP,
            currencyPrecisions = sortedMapOf("CNY" to 2),
            merchantNotificationMaxAttempts = 3,
            negativeSettlementPolicy = "MANUAL_REVIEW_NO_AUTO_EXECUTION",
            largeRefundReviewThreshold = null,
            defaultPageSize = 20,
            maxPageSize = 100,
        )
    }
}

data class ReferencePolicy(
    val businessTimezone: String,
    val enabledCurrencies: Set<String>,
    val paymentExpiry: Duration,
    val unknownResultReviewAfter: Duration,
    val operationPollRetryAfterMs: Int,
    val operationObservationTimeout: Duration,
    val refundWindow: Period,
    val maxRefundAttempts: Int,
    val billReadMaxAttempts: Int,
    val billReadBackoff: Duration,
    val feeRate: BigDecimal,
    val feeBasisPoints: Int,
    val fixedFees: Map<String, BigDecimal>,
    val roundingMode: RoundingMode,
    val currencyPrecisions: Map<String, Int>,
    val merchantNotificationMaxAttempts: Int,
    val negativeSettlementPolicy: String,
    val largeRefundReviewThreshold: BigDecimal?,
    val defaultPageSize: Int,
    val maxPageSize: Int,
)

data class ReferencePolicyOverride(
    val businessTimezone: String? = null,
    val paymentExpiry: Duration? = null,
    val unknownResultReviewAfter: Duration? = null,
    val operationPollRetryAfterMs: Int? = null,
    val operationObservationTimeout: Duration? = null,
    val refundWindow: Period? = null,
    val maxRefundAttempts: Int? = null,
    val billReadMaxAttempts: Int? = null,
    val billReadBackoff: Duration? = null,
    val feeRate: BigDecimal? = null,
    val fixedFees: Map<String, BigDecimal>? = null,
    val roundingMode: String? = null,
    val currencyPrecisions: Map<String, Int>? = null,
    val enabledCurrencies: Set<String>? = null,
    val merchantNotificationMaxAttempts: Int? = null,
    val negativeSettlementPolicy: String? = null,
    val largeRefundReviewThreshold: BigDecimal? = null,
    val defaultPageSize: Int? = null,
    val maxPageSize: Int? = null,
)
