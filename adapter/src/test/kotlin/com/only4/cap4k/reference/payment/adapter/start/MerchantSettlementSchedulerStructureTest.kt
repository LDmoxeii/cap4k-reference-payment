package com.only4.cap4k.reference.payment.adapter.start

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MerchantSettlementSchedulerStructureTest {
    @Test
    fun `daily scheduler uses Asia Shanghai and only sends the daily command`() {
        val source = Files.readString(Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/start/DailyMerchantSettlementScheduler.kt"))
        assertEquals(1, Regex("@Scheduled\\(").findAll(source).count())
        assertContains(source, "zone = \"Asia/Shanghai\"")
        assertContains(source, "Mediator.commands.send(")
        assertContains(source, "RunDailyMerchantSettlementCmd.Request(")
        assertFalse(Regex("(?i)repository").containsMatchIn(source))
    }

    @Test
    fun `unknown review scheduler only sends the review command`() {
        val source = Files.readString(Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/start/UnknownMerchantSettlementReviewScheduler.kt"))
        assertEquals(1, Regex("@Scheduled\\(").findAll(source).count())
        assertContains(source, "Mediator.commands.send(")
        assertContains(source, "ReviewUnknownMerchantSettlementsCmd.Request(")
        assertFalse(Regex("(?i)repository").containsMatchIn(source))
    }
}
