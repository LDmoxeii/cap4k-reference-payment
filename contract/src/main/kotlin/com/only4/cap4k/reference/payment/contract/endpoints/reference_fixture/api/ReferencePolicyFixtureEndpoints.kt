package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/** Reference-only policy controls. They alter fixture input, never persisted business facts. */
@DesignBlockMetadata(tag = "endpoint", name = "GetReferencePolicyEndpoint", packageName = "reference_fixture.api", description = "GET /api/reference-fixtures/policy", aggregates = [], operationName = "reference-fixture.policy.get", family = "endpoint")
object GetReferencePolicyEndpoint {
    const val OPERATION_NAME = "reference-fixture.policy.get"
    data class Request(val ignored: String? = null) : EndpointRequest<Response>
    data class Response(val policy: ReferencePolicyFixtureView)
}

@DesignBlockMetadata(tag = "endpoint", name = "ConfigureReferencePolicyEndpoint", packageName = "reference_fixture.api", description = "POST /api/reference-fixtures/policy", aggregates = [], operationName = "reference-fixture.policy.configure", family = "endpoint")
object ConfigureReferencePolicyEndpoint {
    const val OPERATION_NAME = "reference-fixture.policy.configure"
    data class Request(
        val businessTimezone: String? = null,
        /** ISO-8601 duration, for example PT30M. */
        val paymentExpiry: String? = null,
        /** ISO-8601 duration, for example PT5M. */
        val unknownResultReviewAfter: String? = null,
        val operationPollRetryAfterMs: Int? = null,
        /** ISO-8601 duration, for example PT30S. */
        val operationObservationTimeout: String? = null,
        /** ISO-8601 period, for example P30D. */
        val refundWindow: String? = null,
        val maxRefundAttempts: Int? = null,
        val billReadMaxAttempts: Int? = null,
        /** ISO-8601 duration, for example PT1M. */
        val billReadBackoff: String? = null,
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
    ) : EndpointRequest<Response>
    data class Response(val policy: ReferencePolicyFixtureView)
}

@DesignBlockMetadata(tag = "endpoint", name = "ResetReferencePolicyEndpoint", packageName = "reference_fixture.api", description = "POST /api/reference-fixtures/policy/reset", aggregates = [], operationName = "reference-fixture.policy.reset", family = "endpoint")
object ResetReferencePolicyEndpoint {
    const val OPERATION_NAME = "reference-fixture.policy.reset"
    data class Request(val ignored: String? = null) : EndpointRequest<Response>
    data class Response(val policy: ReferencePolicyFixtureView)
}

data class ReferencePolicyFixtureView(
    val businessTimezone: String,
    val enabledCurrencies: List<String>,
    val paymentExpiry: String,
    val unknownResultReviewAfter: String,
    val operationPollRetryAfterMs: Int,
    val operationObservationTimeout: String,
    val refundWindow: String,
    val maxRefundAttempts: Int,
    val billReadMaxAttempts: Int,
    val billReadBackoff: String,
    val feeRate: BigDecimal,
    val feeBasisPoints: Int,
    val fixedFees: Map<String, BigDecimal>,
    val roundingMode: String,
    val currencyPrecisions: Map<String, Int>,
    val merchantNotificationMaxAttempts: Int,
    val negativeSettlementPolicy: String,
    val largeRefundReviewThreshold: BigDecimal?,
    val defaultPageSize: Int,
    val maxPageSize: Int,
)

/** Reference-only clock controls; all values are UTC instants for a stable HTTP fixture. */
@DesignBlockMetadata(tag = "endpoint", name = "GetReferenceClockEndpoint", packageName = "reference_fixture.api", description = "GET /api/reference-fixtures/clock", aggregates = [], operationName = "reference-fixture.clock.get", family = "endpoint")
object GetReferenceClockEndpoint {
    const val OPERATION_NAME = "reference-fixture.clock.get"
    data class Request(val ignored: String? = null) : EndpointRequest<Response>
    data class Response(val instant: Instant, val zone: String, val defaultInstant: Instant)
}

@DesignBlockMetadata(tag = "endpoint", name = "SetReferenceClockEndpoint", packageName = "reference_fixture.api", description = "POST /api/reference-fixtures/clock/set", aggregates = [], operationName = "reference-fixture.clock.set", family = "endpoint")
object SetReferenceClockEndpoint {
    const val OPERATION_NAME = "reference-fixture.clock.set"
    data class Request(val instant: Instant?) : EndpointRequest<Response>
    data class Response(val instant: Instant, val zone: String, val defaultInstant: Instant)
}

@DesignBlockMetadata(tag = "endpoint", name = "AdvanceReferenceClockEndpoint", packageName = "reference_fixture.api", description = "POST /api/reference-fixtures/clock/advance", aggregates = [], operationName = "reference-fixture.clock.advance", family = "endpoint")
object AdvanceReferenceClockEndpoint {
    const val OPERATION_NAME = "reference-fixture.clock.advance"
    /** ISO-8601 duration, for example PT5M. */
    data class Request(val duration: String?) : EndpointRequest<Response>
    data class Response(val instant: Instant, val zone: String, val defaultInstant: Instant)
}

@DesignBlockMetadata(tag = "endpoint", name = "ResetReferenceClockEndpoint", packageName = "reference_fixture.api", description = "POST /api/reference-fixtures/clock/reset", aggregates = [], operationName = "reference-fixture.clock.reset", family = "endpoint")
object ResetReferenceClockEndpoint {
    const val OPERATION_NAME = "reference-fixture.clock.reset"
    data class Request(val ignored: String? = null) : EndpointRequest<Response>
    data class Response(val instant: Instant, val zone: String, val defaultInstant: Instant)
}
