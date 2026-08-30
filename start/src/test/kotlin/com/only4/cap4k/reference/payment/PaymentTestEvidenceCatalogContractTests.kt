package com.only4.cap4k.reference.payment

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * 这不是业务行为测试，而是证据目录的“可执行目录索引”：
 * 目录一旦漏掉 PAY-AC、引用不存在的测试方法，或把 planned 场景写成已验证，构建就会失败。
 */
class PaymentTestEvidenceCatalogContractTests {
    @Test
    fun `catalog enumerates every acceptance scenario exactly once and keeps planned boundaries`() {
        val root = repositoryRoot()
        val catalog = Files.readString(root.resolve(CATALOG))
        val scenarios = Files.readString(root.resolve("docs/requirements/acceptance/payment-scenarios.md"))
        val expectedIds = acceptanceIds(scenarios)
        val catalogRows = Regex("(?m)^\\| (PAY-AC-[0-9]{3}) \\| ([^|]+) \\|")
            .findAll(catalog)
            .map { it.groupValues[1] to it.groupValues[2].trim() }
            .toList()
        val catalogIds = catalogRows.map { it.first }
        val acceptanceStatuses = traceabilityAcceptanceStatuses(root)

        assertThat(expectedIds).hasSize(53)
        assertThat(catalogIds).hasSize(53)
        assertThat(catalogIds.toSet()).containsExactlyInAnyOrderElementsOf(expectedIds)
        assertThat(catalogIds).doesNotHaveDuplicates()
        catalogRows.forEach { (id, catalogStatus) ->
            val expectedStatus = if (catalogStatus == "planned/not-built") "planned" else catalogStatus
            assertThat(acceptanceStatuses[id]).describedAs(id + " catalog status").isEqualTo(expectedStatus)
        }
        assertThat(catalog).contains("Given / Arrange").contains("When / Act").contains("Then / Assert")
        listOf("支付状态", "尝试状态", "成功事实", "复核", "通知意图", "结算资格", "持久化结果")
            .forEach { dimension -> assertThat(catalog).contains(dimension) }

        val planned = listOf("PAY-AC-080", "PAY-AC-081", "PAY-AC-084", "PAY-AC-086")
        planned.forEach { id ->
            val row = catalog.lineSequence().first { it.startsWith("| $id ") }
            assertThat(row).contains("planned/not-built")
            assertThat(row).contains("不能证明")
        }
        assertThat(catalog).contains("PAY-EV-010/025/026")
        assertThat(catalog).contains("不宣称生产能力")
    }

    @Test
    fun `catalog references real test methods and keeps proof boundaries explicit`() {
        val root = repositoryRoot()
        val catalog = Files.readString(root.resolve(CATALOG))
        val testSources = Files.walk(root).use { paths ->
            paths.iterator().asSequence()
                .filter {
                    val normalized = it.toString().replace('\\', '/')
                    normalized.contains("/src/test/") && normalized.endsWith(".kt") && !normalized.contains("/.worktrees/")
                }
                .associate { it.fileName.toString().removeSuffix(".kt") to Files.readString(it) }
        }
        val references = Regex("`([A-Za-z0-9_]+)#([^`]+)`")
            .findAll(catalog)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

        assertThat(references).isNotEmpty()
        references.forEach { (testClass, method) ->
            val source = requireNotNull(testSources[testClass]) { "catalog reference class " + testClass + " does not exist" }
            assertThat(source)
                .describedAs("catalog reference " + testClass + "#" + method)
                .contains("fun `" + method + "`")
        }
        assertThat(catalog).contains("测试 receiver 不是生产商户通知服务")
        assertThat(catalog).contains("Analyzer Flow 是静态证据")
        assertThat(catalog).contains("focused test 不能替代")
    }

    private fun acceptanceIds(text: String): Set<String> =
        Regex("PAY-AC-[0-9]{3}").findAll(text).map { it.value }.toSet()

    @Suppress("UNCHECKED_CAST")
    private fun traceabilityAcceptanceStatuses(root: Path): Map<String, String> {
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
        val document = Files.newBufferedReader(root.resolve("docs/requirements/traceability.yaml")).use {
            Yaml(SafeConstructor(options)).load<Map<String, Any?>>(it)
        }
        return (document.getValue("acceptance") as Map<String, Map<String, Any?>>)
            .mapValues { (_, record) -> record.getValue("status").toString() }
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.exists(it.resolve("docs/requirements/traceability.yaml")) }

    private companion object {
        const val CATALOG = "docs/requirements/acceptance/payment-test-evidence-catalog.md"
    }
}
