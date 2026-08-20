package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ReconciliationEndpointHttpConfigurationContractTest {
    private val source = Files.readString(
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/reconciliation/ReconciliationEndpointHttpConfiguration.kt")
    )

    @Test
    fun `handwritten reconciliation bindings retain factory method path status and mapper contracts`() {
        assertBinding(
            functionName = "getReconciliationBatchHttpBinding",
            method = "GET",
            path = "/api/reconciliation-batches/{batchId}",
            mapperEvidence = listOf("request.path(\"batchId\", String::class)"),
        )
        assertBinding(
            functionName = "rerunReconciliationBatchHttpBinding",
            method = "POST",
            path = "/api/reconciliation-batches/{batchId}/reruns",
            mapperEvidence = listOf(
                "request.body(RerunReconciliationBatchEndpoint.Request::class)",
                "batchId = request.path(\"batchId\", String::class)",
            ),
        )
        assertBinding(
            functionName = "disposeReconciliationDifferenceHttpBinding",
            method = "POST",
            path = "/api/reconciliation-items/{itemId}/dispositions",
            mapperEvidence = listOf(
                "request.body(DisposeReconciliationDifferenceEndpoint.Request::class)",
                "itemId = request.path(\"itemId\", String::class)",
            ),
        )
    }

    private fun assertBinding(
        functionName: String,
        method: String,
        path: String,
        mapperEvidence: List<String>,
    ) {
        val body = functionBody(functionName)
        assertContains(body, "EndpointMvcBinding.special(")
        assertContains(body, "method = HttpMethod.$method")
        assertContains(body, "path = \"$path\"")
        assertContains(body, "responsePolicy = EndpointMvcResponsePolicy.response(status = 200)")
        assertContains(body, "requestMapper = EndpointMvcRequestMapper")
        mapperEvidence.forEach { evidence -> assertContains(body, evidence) }
    }

    private fun functionBody(functionName: String): String {
        val start = source.indexOf("fun $functionName()")
        assertTrue(start >= 0, "binding function $functionName must exist")
        val nextBean = source.indexOf("    @Bean", start + 1).let { if (it == -1) source.length else it }
        return source.substring(start, nextBean)
    }
}
