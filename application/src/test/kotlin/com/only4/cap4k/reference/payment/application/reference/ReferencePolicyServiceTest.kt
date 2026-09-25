package com.only4.cap4k.reference.payment.application.reference

import java.math.BigDecimal
import java.time.Duration
import java.time.Period
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReferencePolicyServiceTest {
    @Test
    fun `PAY-AC-101 policy override is deterministic and reset restores the documented defaults`() {
        val policy = ReferencePolicyService()

        assertEquals(BigDecimal("0.006"), policy.current().feeRate)
        assertEquals(60, policy.current().feeBasisPoints)
        assertEquals("HALF_UP", policy.current().roundingMode.name)
        assertEquals(2, policy.current().currencyPrecisions.getValue("CNY"))
        assertEquals(Duration.ofMinutes(30), policy.current().paymentExpiry)
        assertEquals(Duration.ofMinutes(5), policy.current().unknownResultReviewAfter)
        assertEquals(Period.ofDays(30), policy.current().refundWindow)
        assertEquals(20, policy.pageSize(null))

        policy.configure(
            ReferencePolicyOverride(
                feeRate = BigDecimal("0.008"),
                roundingMode = "down",
                currencyPrecisions = mapOf("CNY" to 3),
                paymentExpiry = Duration.ofMinutes(2),
                unknownResultReviewAfter = Duration.ofMinutes(1),
                refundWindow = Period.ofDays(3),
                operationPollRetryAfterMs = 25,
                operationObservationTimeout = Duration.ofSeconds(5),
                maxRefundAttempts = 4,
                billReadMaxAttempts = 5,
                billReadBackoff = Duration.ofSeconds(10),
                merchantNotificationMaxAttempts = 6,
                defaultPageSize = 2,
                maxPageSize = 5,
            ),
        )

        val rule = policy.settlementFeeRule("cny")
        assertEquals(BigDecimal("0.008"), rule.feeRate)
        assertEquals(80, rule.basisPoints)
        assertEquals("DOWN", rule.roundingMode.name)
        assertEquals(3, rule.currencyPrecision)
        assertEquals(BigDecimal("1.234"), policy.money(BigDecimal("1.234"), "CNY").amount)
        assertEquals(Duration.ofMinutes(2), policy.current().paymentExpiry)
        assertEquals(Duration.ofMinutes(1), policy.current().unknownResultReviewAfter)
        assertEquals(Period.ofDays(3), policy.current().refundWindow)
        assertEquals(25, policy.current().operationPollRetryAfterMs)
        assertEquals(4, policy.current().maxRefundAttempts)
        assertEquals(2, policy.pageSize(null))
        assertEquals(5, policy.pageSize(5))
        assertFailsWith<IllegalArgumentException> { policy.pageSize(6) }

        policy.reset()
        assertEquals(BigDecimal("0.006"), policy.current().feeRate)
        assertEquals("HALF_UP", policy.current().roundingMode.name)
        assertEquals(2, policy.current().currencyPrecisions.getValue("CNY"))
        assertEquals(Duration.ofMinutes(30), policy.current().paymentExpiry)
        assertEquals(Period.ofDays(30), policy.current().refundWindow)
        assertEquals(20, policy.pageSize(null))
        assertFailsWith<IllegalArgumentException> { policy.money(BigDecimal("1.234"), "CNY") }
    }

    @Test
    fun `PAY-AC-014 a captured effective fee policy rule remains unchanged after a later override`() {
        val policy = ReferencePolicyService()
        val effectiveAtFirstSuccess = policy.configure(
            ReferencePolicyOverride(
                feeRate = BigDecimal("0.008"),
                roundingMode = "HALF_UP",
                currencyPrecisions = mapOf("CNY" to 2),
            ),
        )
        val capturedRule = policy.settlementFeeRule("CNY")

        policy.configure(
            ReferencePolicyOverride(
                feeRate = BigDecimal("0.003"),
                roundingMode = "DOWN",
                currencyPrecisions = mapOf("CNY" to 0),
            ),
        )

        assertEquals(BigDecimal("0.008"), effectiveAtFirstSuccess.feeRate)
        assertEquals(BigDecimal("0.008"), capturedRule.feeRate)
        assertEquals(80, capturedRule.basisPoints)
        assertEquals("HALF_UP", capturedRule.roundingMode.name)
        assertEquals(2, capturedRule.currencyPrecision)
        assertEquals(BigDecimal("0.003"), policy.current().feeRate)
        assertEquals("DOWN", policy.current().roundingMode.name)
        assertEquals(0, policy.current().currencyPrecisions.getValue("CNY"))
    }

    @Test
    fun `semantically equivalent trailing-zero fee rate is accepted as the documented default`() {
        val policy = ReferencePolicyService()

        val configured = policy.configure(ReferencePolicyOverride(feeRate = BigDecimal("0.00600")))

        assertEquals(BigDecimal("0.006"), configured.feeRate)
        assertEquals(60, configured.feeBasisPoints)
    }

    @Test
    fun `invalid temporal retry and page overrides do not replace the prior atomic policy`() {
        val policy = ReferencePolicyService()
        val before = policy.current()

        assertFailsWith<IllegalArgumentException> {
            policy.configure(ReferencePolicyOverride(paymentExpiry = Duration.ZERO))
        }
        assertEquals(before, policy.current())
        assertFailsWith<IllegalArgumentException> {
            policy.configure(ReferencePolicyOverride(defaultPageSize = 10, maxPageSize = 5))
        }
        assertEquals(before, policy.current())
        assertFailsWith<IllegalArgumentException> {
            policy.configure(ReferencePolicyOverride(operationPollRetryAfterMs = 0))
        }
        assertEquals(before, policy.current())
    }
}
