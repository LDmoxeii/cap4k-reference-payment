package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class RefundEndpointHttpConfigurationContractTest {
    private val source = Files.readString(
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/refund/RefundEndpointHttpConfiguration.kt")
    )

    @Test
    fun `handwritten refund bindings retain method path status and mapper contracts`() {
        assertBinding(
            functionName = "createRefundHttpBinding",
            factory = "special",
            method = "POST",
            path = "/api/refunds",
            status = 201,
            mapperEvidence = "request.body(CreateRefundEndpoint.Request::class)",
        )
        assertBinding(
            functionName = "confirmRefundResultHttpBinding",
            factory = "json",
            method = "POST",
            path = "/api/channel/refund-results",
            status = null,
            mapperEvidence = null,
        )
        assertBinding(
            functionName = "getRefundHttpBinding",
            factory = "special",
            method = "GET",
            path = "/api/refunds/{refundId}",
            status = 200,
            mapperEvidence = "request.path(\"refundId\", String::class)",
        )
    }

    private fun assertBinding(
        functionName: String,
        factory: String,
        method: String,
        path: String,
        status: Int?,
        mapperEvidence: String?,
    ) {
        val body = functionBody(functionName)
        assertContains(body, "EndpointMvcBinding.$factory(")
        assertContains(body, "method = HttpMethod.$method")
        assertContains(body, "path = \"$path\"")
        if (status != null) {
            assertContains(body, "responsePolicy = EndpointMvcResponsePolicy.response(status = $status)")
        }
        if (mapperEvidence != null) {
            assertContains(body, "requestMapper = EndpointMvcRequestMapper")
            assertContains(body, mapperEvidence)
        }
    }

    private fun functionBody(functionName: String): String {
        val start = source.indexOf("fun $functionName()")
        assertTrue(start >= 0, "binding function $functionName must exist")
        val nextBean = source.indexOf("    @Bean", start + 1).let { if (it == -1) source.length else it }
        return source.substring(start, nextBean)
    }
}
