package com.only4.cap4k.reference.payment.contract

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ContractDependencyLeafTest {
    private val buildScript = Files.readString(Path.of("build.gradle.kts"))

    @Test
    fun `contract stays a project dependency leaf`() {
        listOf("domain", "application", "adapter", "start").forEach { module ->
            assertFalse(buildScript.contains("project(\":$module\")"), "contract must not depend on project(:$module)")
        }
    }

    @Test
    fun `contract does not acquire spring jpa or transport dependencies`() {
        val forbiddenDependencyMarkers = listOf(
            "spring-boot", "spring.context", "spring.web", "spring.data", "jakarta.persistence",
            "hibernate", "endpoint.http", "endpoint.rpc", "transport",
        )

        forbiddenDependencyMarkers.forEach { marker ->
            assertFalse(buildScript.lowercase().contains(marker), "contract build script must not contain $marker")
        }
        assertContains(buildScript, "implementation(libs.cap4k.contract.api)")
    }
}
