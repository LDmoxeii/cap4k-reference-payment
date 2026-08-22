package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class MerchantSettlementEndpointHttpConfigurationContractTest {
    private val source = Files.readString(Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/merchant_settlement/MerchantSettlementEndpointHttpConfiguration.kt"))

    @Test
    fun `handwritten bindings retain all six merchant settlement routes`() {
        assertSpecial("prepareMerchantSettlementHttpBinding", "POST", "/api/merchant-settlements", 201, listOf("request.body(PrepareMerchantSettlementEndpoint.Request::class)"))
        assertSpecial("confirmMerchantSettlementHttpBinding", "POST", "/api/merchant-settlements/{settlementId}/confirmations", 200, listOf("request.body(ConfirmMerchantSettlementEndpoint.Request::class)", "settlementId = request.path(\"settlementId\", String::class)"))
        assertSpecial("startMerchantSettlementExecutionHttpBinding", "POST", "/api/merchant-settlements/{settlementId}/executions", 200, listOf("request.body(StartMerchantSettlementExecutionEndpoint.Request::class)", "settlementId = request.path(\"settlementId\", String::class)"))
        val resultBody = functionBody("confirmMerchantSettlementResultHttpBinding")
        assertContains(resultBody, "EndpointMvcBinding.json(")
        assertContains(resultBody, "method = HttpMethod.POST")
        assertContains(resultBody, "path = \"/api/channel/settlement-results\"")
        assertSpecial("voidMerchantSettlementHttpBinding", "POST", "/api/merchant-settlements/{settlementId}/voids", 200, listOf("request.body(VoidMerchantSettlementEndpoint.Request::class)", "settlementId = request.path(\"settlementId\", String::class)"))
        assertSpecial("getMerchantSettlementHttpBinding", "GET", "/api/merchant-settlements/{settlementId}", 200, listOf("GetMerchantSettlementEndpoint.Request(request.path(\"settlementId\", String::class))"))
    }

    private fun assertSpecial(functionName: String, method: String, path: String, status: Int, evidence: List<String>) {
        val body = functionBody(functionName)
        assertContains(body, "EndpointMvcBinding.special(")
        assertContains(body, "method = HttpMethod.$method")
        assertContains(body, "path = \"$path\"")
        assertContains(body, "responsePolicy = EndpointMvcResponsePolicy.response(status = $status)")
        assertContains(body, "requestMapper = EndpointMvcRequestMapper")
        evidence.forEach { assertContains(body, it) }
    }

    private fun functionBody(functionName: String): String {
        val start = source.indexOf("fun $functionName()")
        assertTrue(start >= 0, "binding function $functionName must exist")
        val nextBean = source.indexOf("    @Bean", start + 1).let { if (it == -1) source.length else it }
        return source.substring(start, nextBean)
    }
}
