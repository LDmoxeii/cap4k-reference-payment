package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceLogicalClock
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicy
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyOverride
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.AdvanceReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ReferencePolicyFixtureView
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.SetReferenceClockEndpoint
import java.time.Duration
import java.time.Period
import org.springframework.stereotype.Component

@Component
class GetReferencePolicyEndpointHandler(
    private val policy: ReferencePolicyService,
) : EndpointHandler<GetReferencePolicyEndpoint.Request, GetReferencePolicyEndpoint.Response> {
    override fun handle(request: GetReferencePolicyEndpoint.Request) = GetReferencePolicyEndpoint.Response(policy.current().toView())
}

@Component
class ConfigureReferencePolicyEndpointHandler(
    private val policy: ReferencePolicyService,
) : EndpointHandler<ConfigureReferencePolicyEndpoint.Request, ConfigureReferencePolicyEndpoint.Response> {
    override fun handle(request: ConfigureReferencePolicyEndpoint.Request) = ConfigureReferencePolicyEndpoint.Response(
        policy.configure(
            ReferencePolicyOverride(
                businessTimezone = request.businessTimezone,
                paymentExpiry = request.paymentExpiry?.let { parseDuration(it, "paymentExpiry") },
                unknownResultReviewAfter = request.unknownResultReviewAfter?.let {
                    parseDuration(it, "unknownResultReviewAfter")
                },
                operationPollRetryAfterMs = request.operationPollRetryAfterMs,
                operationObservationTimeout = request.operationObservationTimeout?.let {
                    parseDuration(it, "operationObservationTimeout")
                },
                refundWindow = request.refundWindow?.let(::parsePeriod),
                maxRefundAttempts = request.maxRefundAttempts,
                billReadMaxAttempts = request.billReadMaxAttempts,
                billReadBackoff = request.billReadBackoff?.let { parseDuration(it, "billReadBackoff") },
                feeRate = request.feeRate,
                fixedFees = request.fixedFees,
                roundingMode = request.roundingMode,
                currencyPrecisions = request.currencyPrecisions,
                enabledCurrencies = request.enabledCurrencies,
                merchantNotificationMaxAttempts = request.merchantNotificationMaxAttempts,
                negativeSettlementPolicy = request.negativeSettlementPolicy,
                largeRefundReviewThreshold = request.largeRefundReviewThreshold,
                defaultPageSize = request.defaultPageSize,
                maxPageSize = request.maxPageSize,
            ),
        ).toView(),
    )
}

@Component
class ResetReferencePolicyEndpointHandler(
    private val policy: ReferencePolicyService,
) : EndpointHandler<ResetReferencePolicyEndpoint.Request, ResetReferencePolicyEndpoint.Response> {
    override fun handle(request: ResetReferencePolicyEndpoint.Request) = ResetReferencePolicyEndpoint.Response(policy.reset().toView())
}

@Component
class GetReferenceClockEndpointHandler(
    private val clock: ReferenceLogicalClock,
) : EndpointHandler<GetReferenceClockEndpoint.Request, GetReferenceClockEndpoint.Response> {
    override fun handle(request: GetReferenceClockEndpoint.Request): GetReferenceClockEndpoint.Response = clockState(clock).let { state ->
        GetReferenceClockEndpoint.Response(state.instant, state.zone, state.defaultInstant)
    }
}

@Component
class SetReferenceClockEndpointHandler(
    private val clock: ReferenceLogicalClock,
) : EndpointHandler<SetReferenceClockEndpoint.Request, SetReferenceClockEndpoint.Response> {
    override fun handle(request: SetReferenceClockEndpoint.Request): SetReferenceClockEndpoint.Response {
        val instant = request.instant ?: throw IllegalArgumentException("logical clock instant 不能为空")
        return clockState(clock.set(instant), clock).let { state ->
            SetReferenceClockEndpoint.Response(state.instant, state.zone, state.defaultInstant)
        }
    }
}

@Component
class AdvanceReferenceClockEndpointHandler(
    private val clock: ReferenceLogicalClock,
) : EndpointHandler<AdvanceReferenceClockEndpoint.Request, AdvanceReferenceClockEndpoint.Response> {
    override fun handle(request: AdvanceReferenceClockEndpoint.Request): AdvanceReferenceClockEndpoint.Response {
        val duration = request.duration?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Duration.parse(it) }.getOrElse { throw IllegalArgumentException("非法逻辑时钟 duration：$it") } }
            ?: throw IllegalArgumentException("logical clock duration 不能为空")
        return clockState(clock.advance(duration), clock).let { state ->
            AdvanceReferenceClockEndpoint.Response(state.instant, state.zone, state.defaultInstant)
        }
    }
}

@Component
class ResetReferenceClockEndpointHandler(
    private val clock: ReferenceLogicalClock,
) : EndpointHandler<ResetReferenceClockEndpoint.Request, ResetReferenceClockEndpoint.Response> {
    override fun handle(request: ResetReferenceClockEndpoint.Request): ResetReferenceClockEndpoint.Response =
        clockState(clock.reset(), clock).let { state ->
            ResetReferenceClockEndpoint.Response(state.instant, state.zone, state.defaultInstant)
        }
}

private fun ReferencePolicy.toView() = ReferencePolicyFixtureView(
    businessTimezone = businessTimezone,
    enabledCurrencies = enabledCurrencies.toList().sorted(),
    paymentExpiry = paymentExpiry.toString(),
    unknownResultReviewAfter = unknownResultReviewAfter.toString(),
    operationPollRetryAfterMs = operationPollRetryAfterMs,
    operationObservationTimeout = operationObservationTimeout.toString(),
    refundWindow = refundWindow.toString(),
    maxRefundAttempts = maxRefundAttempts,
    billReadMaxAttempts = billReadMaxAttempts,
    billReadBackoff = billReadBackoff.toString(),
    feeRate = feeRate,
    feeBasisPoints = feeBasisPoints,
    fixedFees = fixedFees.toSortedMap(),
    roundingMode = roundingMode.name,
    currencyPrecisions = currencyPrecisions.toSortedMap(),
    merchantNotificationMaxAttempts = merchantNotificationMaxAttempts,
    negativeSettlementPolicy = negativeSettlementPolicy,
    largeRefundReviewThreshold = largeRefundReviewThreshold,
    defaultPageSize = defaultPageSize,
    maxPageSize = maxPageSize,
)

private fun parseDuration(raw: String, field: String): Duration = raw.trim().takeIf(String::isNotEmpty)?.let { value ->
    runCatching { Duration.parse(value) }.getOrElse { throw IllegalArgumentException("非法 $field：$raw") }
} ?: throw IllegalArgumentException("$field 不能为空")

private fun parsePeriod(raw: String): Period = raw.trim().takeIf(String::isNotEmpty)?.let { value ->
    runCatching { Period.parse(value) }.getOrElse { throw IllegalArgumentException("非法 refundWindow：$raw") }
} ?: throw IllegalArgumentException("refundWindow 不能为空")

private data class ReferenceClockState(val instant: java.time.Instant, val zone: String, val defaultInstant: java.time.Instant)

private fun clockState(clock: ReferenceLogicalClock): ReferenceClockState =
    clockState(clock.instant(), clock)

private fun clockState(instant: java.time.Instant, clock: ReferenceLogicalClock): ReferenceClockState =
    ReferenceClockState(instant, clock.zone.id, ReferenceLogicalClock.DEFAULT_INSTANT)
