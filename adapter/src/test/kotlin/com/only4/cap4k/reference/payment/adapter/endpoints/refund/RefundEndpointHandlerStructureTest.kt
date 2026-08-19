package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RefundEndpointHandlerStructureTest {
    private val sourceRoot: Path =
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/refund")

    @Test
    fun `each refund endpoint handler is one class in its own file and uses static mediator dispatch`() {
        val expectations = mapOf(
            "CreateRefundEndpointHandler.kt" to "Mediator.commands.send(",
            "ConfirmRefundResultEndpointHandler.kt" to "Mediator.commands.send(",
            "GetRefundEndpointHandler.kt" to "Mediator.queries.ask(",
        )

        expectations.forEach { (fileName, dispatch) ->
            val source = read(fileName)
            val handlerName = fileName.removeSuffix(".kt")
            val declaredClasses = Regex("(?m)^class\\s+([A-Za-z0-9_]+)")
                .findAll(source)
                .map { it.groupValues[1] }
                .toList()

            assertEquals(listOf(handlerName), declaredClasses)
            assertContains(source, dispatch)
            assertFalse(source.contains("private val mediator"))
        }
    }

    @Test
    fun `refund result handler explicitly projects every domain outcome field`() {
        val source = read("ConfirmRefundResultEndpointHandler.kt")
        val mappings = listOf(
            "refundStatus = outcome.refundStatus.name",
            "attemptStatus = outcome.attemptStatus?.name",
            "notificationReceiveCount = outcome.notificationReceiveCount",
            "disposition = outcome.disposition.name",
            "duplicate = outcome.duplicate",
            "accepted = outcome.accepted",
            "rejected = outcome.rejected",
            "conflicting = outcome.conflicting",
            "reservationReleasedNow = outcome.reservationReleasedNow",
            "reservationConvertedToSuccessNow = outcome.reservationConvertedToSuccessNow",
            "reviewRequiredNow = outcome.reviewRequiredNow",
            "rejectionSummary = outcome.rejectionSummary",
            "conflictSummary = outcome.conflictSummary",
        )

        assertContains(source, "Mediator.commands.send(")
        assertContains(source, ").outcome")
        mappings.forEach { mapping -> assertContains(source, mapping) }
    }

    private fun read(fileName: String): String = Files.readString(sourceRoot.resolve(fileName))
}
