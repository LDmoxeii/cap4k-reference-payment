package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MerchantSettlementEndpointHandlerStructureTest {
    private val sourceRoot = Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/merchant_settlement")

    @Test
    fun `each merchant settlement endpoint handler is one class in its own file and uses static mediator dispatch`() {
        val expectations = mapOf(
            "PrepareMerchantSettlementEndpointHandler.kt" to "Mediator.commands.send(",
            "ConfirmMerchantSettlementEndpointHandler.kt" to "Mediator.commands.send(",
            "StartMerchantSettlementExecutionEndpointHandler.kt" to "Mediator.commands.send(",
            "ConfirmMerchantSettlementResultEndpointHandler.kt" to "Mediator.commands.send(",
            "VoidMerchantSettlementEndpointHandler.kt" to "Mediator.commands.send(",
            "GetMerchantSettlementEndpointHandler.kt" to "Mediator.queries.ask(",
        )
        expectations.forEach { (fileName, dispatch) ->
            val source = Files.readString(sourceRoot.resolve(fileName))
            val handlerName = fileName.removeSuffix(".kt")
            val declaredClasses = Regex("(?m)^class\\s+([A-Za-z0-9_]+)").findAll(source).map { it.groupValues[1] }.toList()
            assertEquals(listOf(handlerName), declaredClasses)
            assertContains(source, dispatch)
            assertFalse(source.contains("private val mediator"))
        }
    }
}
