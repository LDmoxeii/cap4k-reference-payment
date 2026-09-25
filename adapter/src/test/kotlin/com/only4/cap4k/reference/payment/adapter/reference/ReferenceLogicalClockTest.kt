package com.only4.cap4k.reference.payment.adapter.reference

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReferenceLogicalClockTest {
    @Test
    fun `reference logical clock supports deterministic read set advance and reset`() {
        val clock = ReferenceLogicalClock()
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), clock.instant())
        clock.set(Instant.parse("2026-02-03T04:05:06Z"))
        assertEquals(Instant.parse("2026-02-03T04:10:06Z"), clock.advance(Duration.ofMinutes(5)))
        assertFailsWith<IllegalArgumentException> { clock.advance(Duration.ofSeconds(-1)) }
        assertEquals(ReferenceLogicalClock.DEFAULT_INSTANT, clock.reset())
    }
}
