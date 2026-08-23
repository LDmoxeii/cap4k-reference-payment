package com.only4.cap4k.reference.payment

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

class AcceptanceGuideContractTests {
    @Test
    fun `guide covers every acceptance id and preserves traceability statuses`() {
        val root = repositoryRoot()
        val scenarios = Files.readString(root.resolve("docs/requirements/acceptance/payment-scenarios.md"))
        val requirementsReadme = Files.readString(root.resolve("docs/requirements/README.md"))
        val projectReadme = Files.readString(root.resolve("README.md"))
        val guide = Files.readString(root.resolve("docs/requirements/acceptance/payment-acceptance-guide.md"))
        assertThat(scenarios).contains("payment-acceptance-guide.md")
        assertThat(requirementsReadme).contains("acceptance/payment-acceptance-guide.md")
        assertThat(projectReadme).contains("docs/requirements/acceptance/payment-acceptance-guide.md")
        val scenarioIds = acceptanceIds(scenarios)
        val guideIds = acceptanceIds(guide)

        assertThat(scenarioIds).hasSize(53)
        assertThat(guideIds).containsAll(scenarioIds)
        assertThat(guide).contains("| `verified` | 49 |").contains("| `planned/not-built` | 4 |")
        assertThat(guide).contains("PAY-AC-080").contains("PAY-AC-081").contains("PAY-AC-084").contains("PAY-AC-086")

        val acceptance = traceabilityAcceptance(root)
        assertThat(acceptance.values.count { it["status"] == "verified" }).isEqualTo(49)
        assertThat(acceptance.filterValues { it["status"] == "planned" }.keys)
            .containsExactlyInAnyOrder("PAY-AC-080", "PAY-AC-081", "PAY-AC-084", "PAY-AC-086")
    }

    @Test
    fun `guide links are portable and resolve inside the repository`() {
        val root = repositoryRoot()
        val guidePath = root.resolve("docs/requirements/acceptance/payment-acceptance-guide.md")
        val guide = Files.readString(guidePath)
        assertThat(guide).doesNotMatch("(?m)^\\s*[A-Za-z]:[\\\\/].*")

        val links = Regex("""\[[^]]+]\(([^)]+)\)""")
            .findAll(guide)
            .map { it.groupValues[1].substringBefore('#') }
            .filter { it.isNotBlank() && !it.startsWith("http") }
            .toSet()
        links.forEach { link ->
            assertThat(guidePath.parent.resolve(link).normalize())
                .describedAs("guide link $link")
                .exists()
        }
    }

    @Test
    fun `guide names real precise test methods for all four business families`() {
        val root = repositoryRoot()
        val guide = Files.readString(root.resolve("docs/requirements/acceptance/payment-acceptance-guide.md"))
        listOf(
            "create attempt confirm duplicate conflict and query form one durable payment chain",
            "a successful payment can be refunded in full",
            "daily reconciliation matches payment and refund facts and exposes one effective run",
            "merchant settlement lifecycle produces net 127 and preserves callback evidence",
            "payment refund reconciliation and settlement preserve one durable composition trail",
        ).forEach { method -> assertThat(guide).contains(method) }
        assertThat(root.resolve("flows").toFile().walkTopDown().count { it.isFile && it.extension == "mmd" }).isGreaterThanOrEqualTo(19)
    }

    private fun acceptanceIds(text: String): Set<String> =
        Regex("PAY-AC-[0-9]{3}").findAll(text).map { it.value }.toSet()

    @Suppress("UNCHECKED_CAST")
    private fun traceabilityAcceptance(root: Path): Map<String, Map<String, String>> {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
        val document = Files.newBufferedReader(root.resolve("docs/requirements/traceability.yaml")).use {
            Yaml(SafeConstructor(options)).load<Map<String, Any?>>(it)
        }
        return (document.getValue("acceptance") as Map<String, Map<String, Any?>>)
            .mapValues { (_, value) -> value.mapValues { it.value.toString() } }
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.exists(it.resolve("docs/requirements/traceability.yaml")) }
}
