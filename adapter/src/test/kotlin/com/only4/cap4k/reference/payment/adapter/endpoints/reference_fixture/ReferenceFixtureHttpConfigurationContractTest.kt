package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReferenceFixtureHttpConfigurationContractTest {
    @Test
    fun `reference-only callback evidence binding has an explicit fixture route and no verdict input`() {
        val binding = Files.readString(
            Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/reference_fixture/ReferenceFixtureHttpConfiguration.kt"),
        )
        val contract = Files.readString(
            Path.of("../contract/src/main/kotlin/com/only4/cap4k/reference/payment/contract/endpoints/reference_fixture/api/RegisterReferenceCallbackEvidenceEndpoint.kt"),
        )

        assertContains(binding, "path = \"/api/reference-fixtures/callback-evidence\"")
        assertContains(binding, "method = HttpMethod.POST")
        assertContains(binding, "responsePolicy = EndpointMvcResponsePolicy.response(status = 201)")
        assertContains(contract, "val rawPayload: String?")
        assertFalse(contract.contains("verificationMaterial"))
        assertFalse(contract.contains("verified:"))
        assertFalse(contract.contains("serverHeldProof"))
    }

    @Test
    fun `policy and logical-clock fixture controls expose explicit stable HTTP routes`() {
        val binding = Files.readString(
            Path.of("src/main/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/reference_fixture/ReferenceFixtureHttpConfiguration.kt"),
        )

        assertContains(binding, "path = \"/api/reference-fixtures/policy\"")
        assertContains(binding, "path = \"/api/reference-fixtures/policy/reset\"")
        assertContains(binding, "path = \"/api/reference-fixtures/clock\"")
        assertContains(binding, "path = \"/api/reference-fixtures/clock/set\"")
        assertContains(binding, "path = \"/api/reference-fixtures/clock/advance\"")
        assertContains(binding, "path = \"/api/reference-fixtures/clock/reset\"")
        assertContains(binding, "method = HttpMethod.GET")
        assertContains(binding, "method = HttpMethod.POST")
    }
}
