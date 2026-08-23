package com.only4.cap4k.reference.payment.application.commands.payment.review

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AdjudicatePaymentReviewCmdContractTest {
    private val source = Files.readString(
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/application/commands/payment/review/AdjudicatePaymentReviewCmd.kt")
    )

    @Test
    fun `review error code is propagated explicitly instead of being parsed from exception message`() {
        assertContains(source, "catch (error: PaymentReviewException)")
        assertContains(source, "PaymentConflictException(error.code")
        assertFalse(source.contains("startsWith(\"REVIEW_\")"))
        assertFalse(source.contains("error.message?.takeIf"))
    }
}
