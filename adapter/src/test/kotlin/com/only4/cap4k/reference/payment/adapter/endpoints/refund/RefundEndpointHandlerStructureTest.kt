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
            "CreateRefundEndpointHandler.kt" to ("RequestRefundEndpointHandler" to "Mediator.commands.send("),
            "CreateRefundAttemptEndpointHandler.kt" to ("CreateRefundAttemptEndpointHandler" to "Mediator.commands.send("),
            "SubmitRefundAttemptEndpointHandler.kt" to ("SubmitRefundAttemptEndpointHandler" to "Mediator.commands.send("),
            "ConfirmRefundResultEndpointHandler.kt" to ("ConfirmRefundResultEndpointHandler" to "Mediator.commands.send("),
            "GetRefundEndpointHandler.kt" to ("GetRefundEndpointHandler" to "Mediator.queries.ask("),
        )

        expectations.forEach { (fileName, expectation) ->
            val source = read(fileName)
            val (handlerName, dispatch) = expectation
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
            "refundStatus = ReferenceContractStatusMapper.refundStatus(outcome.refundStatus)",
            "finality = ReferenceContractStatusMapper.refundFinality(",
            "attemptStatus = outcome.attemptStatus?.let(ReferenceContractStatusMapper::refundAttemptStatus)",
            "notificationReceiveCount = outcome.notificationReceiveCount",
            "disposition = ReferenceContractStatusMapper.refundDisposition(outcome.disposition)",
            "duplicate = outcome.duplicate",
            "accepted = outcome.accepted",
            "rejected = outcome.rejected",
            "conflicting = outcome.conflicting",
            "reservationReleasedNow = outcome.reservationReleasedNow",
            "reservationConvertedToSuccessNow = outcome.reservationConvertedToSuccessNow",
            "reviewRequiredNow = outcome.reviewRequiredNow",
            "rejectionSummary = outcome.rejectionSummary",
            "conflictSummary = outcome.conflictSummary",
            "receipt = commandResponse.receipt",
        )

        assertContains(source, "val commandResponse = Mediator.commands.send(")
        assertContains(source, "val outcome = commandResponse.outcome")
        mappings.forEach { mapping -> assertContains(source, mapping) }
    }

    private fun read(fileName: String): String = Files.readString(sourceRoot.resolve(fileName))
}
