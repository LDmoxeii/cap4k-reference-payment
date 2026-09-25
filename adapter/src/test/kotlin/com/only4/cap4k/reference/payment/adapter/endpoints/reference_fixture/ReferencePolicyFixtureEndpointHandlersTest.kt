package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.reference.payment.adapter.reference.ReferenceLogicalClock
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.AdvanceReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.SetReferenceClockEndpoint
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferencePolicyFixtureEndpointHandlersTest {
    private val policy = ReferencePolicyService()
    private val clock = ReferenceLogicalClock()

    @Test
    fun `reference policy fixture reads configured values and reset restores defaults`() {
        val get = GetReferencePolicyEndpointHandler(policy)
        val configure = ConfigureReferencePolicyEndpointHandler(policy)
        val reset = ResetReferencePolicyEndpointHandler(policy)

        assertEquals(BigDecimal("0.006"), get.handle(GetReferencePolicyEndpoint.Request()).policy.feeRate)
        assertEquals(60, get.handle(GetReferencePolicyEndpoint.Request()).policy.feeBasisPoints)
        assertEquals("HALF_UP", get.handle(GetReferencePolicyEndpoint.Request()).policy.roundingMode)
        assertEquals(2, get.handle(GetReferencePolicyEndpoint.Request()).policy.currencyPrecisions.getValue("CNY"))
        assertEquals("PT30M", get.handle(GetReferencePolicyEndpoint.Request()).policy.paymentExpiry)
        assertEquals("PT5M", get.handle(GetReferencePolicyEndpoint.Request()).policy.unknownResultReviewAfter)
        assertEquals("P30D", get.handle(GetReferencePolicyEndpoint.Request()).policy.refundWindow)
        assertEquals(20, get.handle(GetReferencePolicyEndpoint.Request()).policy.defaultPageSize)

        val configured = configure.handle(
            ConfigureReferencePolicyEndpoint.Request(
                feeRate = BigDecimal("0.008"),
                roundingMode = "DOWN",
                currencyPrecisions = mapOf("CNY" to 3),
                paymentExpiry = "PT2M",
                unknownResultReviewAfter = "PT1M",
                refundWindow = "P3D",
                operationPollRetryAfterMs = 25,
                operationObservationTimeout = "PT5S",
                maxRefundAttempts = 4,
                billReadMaxAttempts = 5,
                billReadBackoff = "PT10S",
                merchantNotificationMaxAttempts = 6,
                defaultPageSize = 2,
                maxPageSize = 5,
            ),
        ).policy
        assertEquals(BigDecimal("0.008"), configured.feeRate)
        assertEquals(80, configured.feeBasisPoints)
        assertEquals("DOWN", configured.roundingMode)
        assertEquals(3, configured.currencyPrecisions.getValue("CNY"))
        assertEquals("PT2M", configured.paymentExpiry)
        assertEquals("PT1M", configured.unknownResultReviewAfter)
        assertEquals("P3D", configured.refundWindow)
        assertEquals(25, configured.operationPollRetryAfterMs)
        assertEquals(2, configured.defaultPageSize)
        assertEquals(5, configured.maxPageSize)
        assertEquals(configured, get.handle(GetReferencePolicyEndpoint.Request()).policy)

        val resetPolicy = reset.handle(ResetReferencePolicyEndpoint.Request()).policy
        assertEquals(BigDecimal("0.006"), resetPolicy.feeRate)
        assertEquals(60, resetPolicy.feeBasisPoints)
        assertEquals("HALF_UP", resetPolicy.roundingMode)
        assertEquals(2, resetPolicy.currencyPrecisions.getValue("CNY"))
        assertEquals("PT30M", resetPolicy.paymentExpiry)
        assertEquals("P30D", resetPolicy.refundWindow)
        assertEquals(20, resetPolicy.defaultPageSize)
    }

    @Test
    fun `reference logical clock supports read set advance and reset without wall clock time`() {
        val get = GetReferenceClockEndpointHandler(clock)
        val set = SetReferenceClockEndpointHandler(clock)
        val advance = AdvanceReferenceClockEndpointHandler(clock)
        val reset = ResetReferenceClockEndpointHandler(clock)
        val expectedSet = Instant.parse("2026-08-17T08:00:00Z")

        assertEquals(ReferenceLogicalClock.DEFAULT_INSTANT, get.handle(GetReferenceClockEndpoint.Request()).instant)
        assertEquals(expectedSet, set.handle(SetReferenceClockEndpoint.Request(expectedSet)).instant)
        assertEquals(
            Instant.parse("2026-08-17T08:05:30Z"),
            advance.handle(AdvanceReferenceClockEndpoint.Request("PT5M30S")).instant,
        )
        val resetState = reset.handle(ResetReferenceClockEndpoint.Request())
        assertEquals(ReferenceLogicalClock.DEFAULT_INSTANT, resetState.instant)
        assertEquals(ReferenceLogicalClock.DEFAULT_INSTANT, resetState.defaultInstant)
        assertEquals("Z", resetState.zone)
    }
}
