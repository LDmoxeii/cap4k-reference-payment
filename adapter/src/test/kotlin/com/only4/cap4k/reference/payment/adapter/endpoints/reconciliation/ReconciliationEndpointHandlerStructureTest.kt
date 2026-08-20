package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReconciliationEndpointHandlerStructureTest {
    private val sourceRoot: Path =
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/reconciliation")

    @Test
    fun `each reconciliation endpoint handler is one class in its own file and uses static mediator dispatch`() {
        val expectations = mapOf(
            "GetReconciliationBatchEndpointHandler.kt" to "Mediator.queries.ask(",
            "RerunReconciliationBatchEndpointHandler.kt" to "Mediator.commands.send(",
            "DisposeReconciliationDifferenceEndpointHandler.kt" to "Mediator.commands.send(",
        )

        expectations.forEach { (fileName, dispatch) ->
            val source = Files.readString(sourceRoot.resolve(fileName))
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
}
