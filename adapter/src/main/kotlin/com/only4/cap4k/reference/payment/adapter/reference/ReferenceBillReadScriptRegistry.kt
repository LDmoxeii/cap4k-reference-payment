package com.only4.cap4k.reference.payment.adapter.reference

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.stereotype.Component

/** Reference-only provider script. It can make the provider temporarily unreadable, never decide matching. */
@Component
class ReferenceBillReadScriptRegistry {
    private val remainingUnavailableReads = ConcurrentHashMap<Scope, AtomicInteger>()

    fun scriptUnavailableReads(channelId: String, billIdentity: String, count: Int) {
        require(count >= 0) { "账单暂不可读次数不能为负" }
        remainingUnavailableReads[Scope(channelId, billIdentity)] = AtomicInteger(count)
    }

    fun consumeUnavailableRead(channelId: String, billIdentity: String): Boolean {
        val counter = remainingUnavailableReads[Scope(channelId, billIdentity)] ?: return false
        while (true) {
            val current = counter.get()
            if (current <= 0) return false
            if (counter.compareAndSet(current, current - 1)) return true
        }
    }

    private data class Scope(val channelId: String, val billIdentity: String)
}
