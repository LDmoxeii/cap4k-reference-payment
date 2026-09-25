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
    fun `guide covers all unified acceptance ids and preserves verified traceability`() {
        val root = repositoryRoot()
        val scenarios = Files.readString(root.resolve("docs/requirements/acceptance/payment-scenarios.md"))
        val registry = Files.readString(root.resolve("start/src/test/resources/reference-http/pay-ac-scenarios.json"))
        val requirementsReadme = Files.readString(root.resolve("docs/requirements/README.md"))
        val projectReadme = Files.readString(root.resolve("README.md"))
        val guide = Files.readString(root.resolve("docs/requirements/acceptance/payment-acceptance-guide.md"))
        assertThat(scenarios).contains("payment-acceptance-guide.md")
        assertThat(requirementsReadme).contains("acceptance/payment-acceptance-guide.md")
        assertThat(projectReadme).contains("docs/requirements/acceptance/payment-acceptance-guide.md")
        val scenarioIds = acceptanceIds(registry)
        val guideIds = acceptanceIds(guide)

        assertThat(acceptanceIds(scenarios)).hasSize(53)
        assertThat(scenarioIds).hasSize(67)
        assertThat(guideIds).containsAll(scenarioIds)
        assertThat(guide)
            .contains("62/62 verified")
            .contains("67/67 verified")
            .contains("67/67 passed")
            .contains("2/2 passed")
            .contains("PAY-AC-090")
            .contains("PAY-AC-103")

        val acceptance = traceabilityAcceptance(root)
        assertThat(acceptance).hasSize(67)
        assertThat(acceptance.values.count { it["status"] == "verified" }).isEqualTo(67)
        assertThat(acceptance.filterValues { it["status"] != "verified" }).isEmpty()
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
    fun `guide names the real process-out runner and evidence obligations`() {
        val root = repositoryRoot()
        val guide = Files.readString(root.resolve("docs/requirements/acceptance/payment-acceptance-guide.md"))
        listOf(
            "scripts\\acceptance\\run-pay-ac-http.ps1",
            "独立 Java 17 CAP4K JVM",
            "进程外 PowerShell HTTP client",
            "PAY-AC-102 的场景证据、额外两次 clean loop、PAY-AC-103",
            "不修改或声称 WOW 的验收状态",
        ).forEach { obligation -> assertThat(guide).contains(obligation) }
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
