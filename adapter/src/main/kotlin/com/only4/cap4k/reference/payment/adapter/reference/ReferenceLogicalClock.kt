package com.only4.cap4k.reference.payment.adapter.reference

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-local, deterministic clock for the reference fixture.  Business handlers only depend
 * on [Clock], so tests and the reference HTTP surface can advance time without mutating domain
 * records or waiting in wall-clock time.
 */
class ReferenceLogicalClock(
    private val defaultInstant: Instant = DEFAULT_INSTANT,
    private val clockZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    private val current = AtomicReference(defaultInstant)

    override fun getZone(): ZoneId = clockZone

    override fun withZone(zone: ZoneId): Clock = if (zone == clockZone) this else ReferenceLogicalClock(instant(), zone)

    override fun instant(): Instant = current.get()

    @Synchronized
    fun set(instant: Instant): Instant = instant.also(current::set)

    @Synchronized
    fun advance(duration: Duration): Instant {
        require(!duration.isNegative) { "逻辑时钟只允许向前推进" }
        return current.updateAndGet { it.plus(duration) }
    }

    @Synchronized
    fun reset(): Instant = defaultInstant.also(current::set)

    companion object {
        val DEFAULT_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
