package com.only4.cap4k.reference.payment.adapter.start

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PaymentExpirySchedulerStructureTest {
    private val source = Files.readString(
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/start/PaymentExpiryScheduler.kt")
    )

    @Test
    fun `scheduler is a thin local trigger that only dispatches the expiry command`() {
        assertEquals(1, Regex("@Scheduled\\(").findAll(source).count())
        assertContains(source, "Mediator.commands.send(")
        assertContains(source, "ExpirePaymentsCmd.Request(clock.instant())")
        assertFalse(source.contains("Mediator.queries"))
        assertFalse(source.contains("Repository"))
        assertFalse(Regex("(?i)repository|distributed|exactly-once|persistent scheduler").containsMatchIn(source))
    }
}
