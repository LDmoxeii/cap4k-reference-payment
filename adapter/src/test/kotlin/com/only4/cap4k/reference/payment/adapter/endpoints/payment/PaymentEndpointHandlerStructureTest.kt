package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PaymentEndpointHandlerStructureTest {
    private val sourceRoot: Path = Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/payment")

    @Test
    fun `each endpoint handler is one class in its own file and uses static mediator dispatch`() {
        val expectations = mapOf(
            "CreatePaymentEndpointHandler.kt" to "Mediator.commands.send(",
            "StartPaymentAttemptEndpointHandler.kt" to "Mediator.commands.send(",
            "ConfirmPaymentResultEndpointHandler.kt" to "Mediator.commands.send(",
            "AdjudicatePaymentReviewEndpointHandler.kt" to "Mediator.commands.send(",
            "GetPaymentEndpointHandler.kt" to "Mediator.queries.ask(",
        )

        expectations.forEach { (fileName, dispatch) ->
            val source = read(fileName)
            val handlerName = fileName.removeSuffix(".kt")
            val declaredClasses = Regex("(?m)^class\\s+([A-Za-z0-9_]+)").findAll(source).map { it.groupValues[1] }.toList()

            assertEquals(listOf(handlerName), declaredClasses)
            assertContains(source, dispatch)
            assertFalse(source.contains("private val mediator"))
        }
    }

    @Test
    fun `confirm result handler maps every domain outcome field at the contract boundary`() {
        val source = read("ConfirmPaymentResultEndpointHandler.kt")
        val mappings = listOf(
            "paymentStatus = outcome.paymentStatus.name",
            "attemptStatus = outcome.attemptStatus?.name",
            "notificationReceiveCount = outcome.notificationReceiveCount",
            "disposition = outcome.disposition.name",
            "duplicate = outcome.duplicate",
            "accepted = outcome.accepted",
            "rejected = outcome.rejected",
            "conflicting = outcome.conflicting",
            "rejectionSummary = outcome.rejectionSummary",
            "conflictSummary = outcome.conflictSummary",
            "successFactFormedNow = outcome.successFactFormedNow",
            "reviewIdentity = outcome.reviewIdentity",
            "settlementEligible = outcome.settlementEligible",
            "notificationIntentState = outcome.notificationIntentState?.name",
        )

        assertContains(source, "Mediator.commands.send(")
        assertContains(source, ").outcome")
        mappings.forEach { mapping -> assertContains(source, mapping) }
    }

    private fun read(fileName: String): String = Files.readString(sourceRoot.resolve(fileName))
}
