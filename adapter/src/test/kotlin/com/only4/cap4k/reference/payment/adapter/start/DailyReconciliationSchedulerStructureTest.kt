package com.only4.cap4k.reference.payment.adapter.start

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DailyReconciliationSchedulerStructureTest {
    private val source = Files.readString(
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/start/DailyReconciliationScheduler.kt")
    )

    @Test
    fun `scheduler has one scheduled entrypoint that only dispatches through static mediator`() {
        assertEquals(1, Regex("@Scheduled\\(").findAll(source).count())
        assertContains(source, "Mediator.commands.send(")
        assertContains(source, "RunDailyReconciliationCmd.Request(")
        assertFalse(source.contains("Mediator.queries"))
        assertFalse(source.contains("Repository"))
        assertFalse(Regex("(?i)repository").containsMatchIn(source))
    }
}
