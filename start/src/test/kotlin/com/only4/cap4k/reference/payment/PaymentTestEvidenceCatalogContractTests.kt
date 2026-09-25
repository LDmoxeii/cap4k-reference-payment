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
 * 目录一旦漏掉 PAY-AC、引用不存在的测试方法，或缺少真实 HTTP 证据边界，构建就会失败。
 */
class PaymentTestEvidenceCatalogContractTests {
    @Test
    fun `catalog enumerates all unified acceptance scenarios exactly once as verified`() {
        val root = repositoryRoot()
        val catalog = Files.readString(root.resolve(CATALOG))
        val registry = Files.readString(root.resolve("start/src/test/resources/reference-http/pay-ac-scenarios.json"))
        val expectedIds = acceptanceIds(registry)
        val catalogRows = Regex("(?m)^\\| (PAY-AC-[0-9]{3}) \\| ([^|]+) \\|")
            .findAll(catalog)
            .map { it.groupValues[1] to it.groupValues[2].trim() }
            .toList()
        val catalogIds = catalogRows.map { it.first }
        val acceptanceStatuses = traceabilityAcceptanceStatuses(root)

        assertThat(expectedIds).hasSize(67)
        assertThat(catalogIds).hasSize(67)
        assertThat(catalogIds.toSet()).containsExactlyInAnyOrderElementsOf(expectedIds)
        assertThat(catalogIds).doesNotHaveDuplicates()
        catalogRows.forEach { (id, catalogStatus) ->
            assertThat(catalogStatus).describedAs(id + " catalog status").isEqualTo("verified")
            assertThat(acceptanceStatuses[id]).describedAs(id + " traceability status").isEqualTo("verified")
        }
        assertThat(catalog).contains("Given / Arrange").contains("When / Act").contains("Then / Assert")
        listOf("支付状态", "尝试状态", "成功事实", "复核", "通知意图", "结算资格", "持久化结果")
            .forEach { dimension -> assertThat(catalog).contains(dimension) }

        assertThat(catalog)
            .contains("-EvidenceRoot")
            .contains("build/reference-http-evidence/<runId>/")
            .contains("独立 JVM + 进程外 client")
            .contains("PAY-AC-102.json` + `clean-loop-1/2.json")
            .contains("不声称 WOW 通过")
            .contains("不宣称生产能力")
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

    @Test
    fun `PAY-AC-080 HTTP scenario enforces merchant filtering and cross-merchant rejection`() {
        val block = scenarioBlock("PAY-AC-080")
        assertThat(block)
            .contains("'/api/payments/search'")
            .contains("merchant filter returns only requested merchant")
            .contains("merchant A list excludes merchant B payment")
            .contains("cross-merchant refund is rejected")
            .contains("cross-merchant rejection has no budget side effect")
    }

    @Test
    fun `PAY-AC-084 HTTP scenario rejects retired routing and preserves history`() {
        val block = scenarioBlock("PAY-AC-084")
        assertThat(block)
            .contains("status='RETIRED'")
            .contains("NO_ELIGIBLE_CHANNEL")
            .contains("historical attempt retains channel identity")
            .contains("historical fee snapshot remains unchanged")
    }

    @Test
    fun `PAY-AC-093 HTTP scenario covers five authoritative keyset lists`() {
        val block = scenarioBlock("PAY-AC-093")
        listOf(
            "'/api/payments/search'",
            "'/api/refunds/search'",
            "'/api/reconciliation-runs/search'",
            "'/api/merchant-settlements/search'",
            "'/api/manual-reviews/search'",
        ).forEach { endpoint -> assertThat(block).contains(endpoint) }
        assertThat(block)
            .contains("list supports small-page cursor traversal")
            .contains("cursor pages do not duplicate items")
            .contains("Get-AcForgedCursor")
            .contains("newer payment does not backfill an existing cursor")
    }

    @Test
    fun `PAY-AC-102 harness requires one complete loop and two clean repeats`() {
        val scenario = scenarioBlock("PAY-AC-102")
        val runner = Files.readString(repositoryRoot().resolve("scripts/acceptance/run-pay-ac-http.ps1"))
        assertThat(scenario)
            .contains("Invoke-PayAcCleanLoop")
            .contains("sandbox loop completes payment")
            .contains("sandbox loop completes refund")
            .contains("sandbox loop completes reconciliation")
            .contains("sandbox loop completes settlement")
        assertThat(runner)
            .contains("1..2 | ForEach-Object")
            .contains("cleanLoopsRepeatable = ${'$'}cleanRepeatable")
            .contains("-and -not ${'$'}cleanRepeatable")
    }

    @Test
    fun `PASSED evidence requires at least one recorded assertion`() {
        val suite = Files.readString(repositoryRoot().resolve("scripts/acceptance/PayAcHttpSuite.psm1"))
        assertThat(suite)
            .contains("${'$'}Status -eq 'PASSED' -and ${'$'}assertionCount -eq 0")
            .contains("cannot be PASSED without at least one recorded assertion")
            .contains("assertionCount = ${'$'}assertionCount")
    }

    private fun acceptanceIds(text: String): Set<String> =
        Regex("PAY-AC-[0-9]{3}").findAll(text).map { it.value }.toSet()

    private fun scenarioBlock(id: String): String {
        val source = Files.readString(repositoryRoot().resolve("scripts/acceptance/PayAcScenarios.psm1"))
        val startMarker = "'$id' {"
        val start = source.indexOf(startMarker)
        require(start >= 0) { "missing HTTP scenario $id" }
        val next = source.indexOf("\n        'PAY-AC-", start + startMarker.length)
        return source.substring(start, if (next >= 0) next else source.length)
    }

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
