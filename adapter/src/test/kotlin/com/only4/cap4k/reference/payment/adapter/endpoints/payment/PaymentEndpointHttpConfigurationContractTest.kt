package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PaymentEndpointHttpConfigurationContractTest {
    private val source = Files.readString(
        Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/payment/PaymentEndpointHttpConfiguration.kt")
    )

    @Test
    fun `handwritten bindings retain method path status and mapper contracts`() {
        assertBinding("createPaymentHttpBinding", "special", "POST", "/api/payments", 201, "request.body(CreatePaymentEndpoint.Request::class)")
        assertBinding("startPaymentAttemptHttpBinding", "special", "POST", "/api/payments/{paymentId}/attempts", 200, "request.path(\"paymentId\", String::class)")
        assertBinding("confirmPaymentResultHttpBinding", "json", "POST", "/api/channel/payment-results", null, null)
        assertBinding("getPaymentHttpBinding", "special", "GET", "/api/payments/{paymentId}", 200, "request.path(\"paymentId\", String::class)")
    }

    private fun assertBinding(functionName: String, factory: String, method: String, path: String, status: Int?, mapperEvidence: String?) {
        val body = functionBody(functionName)
        assertContains(body, "EndpointMvcBinding.$factory(")
        assertContains(body, "method = HttpMethod.$method")
        assertContains(body, "path = \"$path\"")
        if (status != null) assertContains(body, "responsePolicy = EndpointMvcResponsePolicy.response(status = $status)")
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
