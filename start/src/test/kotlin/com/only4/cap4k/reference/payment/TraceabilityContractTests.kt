package com.only4.cap4k.reference.payment

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

class TraceabilityContractTests {

    @Test
    fun `verified traceability is internally consistent and preserves deferred boundaries`() {
        val repositoryRoot = repositoryRoot()
        val traceabilityPath = repositoryRoot.resolve("docs/requirements/traceability.yaml")
        val loaderOptions = LoaderOptions().apply { isAllowDuplicateKeys = false }
        val document = Files.newBufferedReader(traceabilityPath).use {
            @Suppress("UNCHECKED_CAST")
            Yaml(SafeConstructor(loaderOptions)).load<Map<String, Any?>>(it)
        }

        val requirements = document.records("requirements")
        val acceptance = document.records("acceptance")
        val acceptanceToRequirements = document.idLists("acceptance_to_requirements")
        val projections = document.records("projections")
        val buildSlices = document.records("build_slices")
        val evidence = document.records("evidence")
        val allowedStatuses = setOf("planned", "not-built", "verified")

        assertPrefixes(requirements, "PAY-BR-")
        assertPrefixes(acceptance, "PAY-AC-")
        assertPrefixes(projections, "PAY-CP-")
        assertPrefixes(evidence, "PAY-EV-")
        assertThat(requirements).hasSize(62)
        assertThat(acceptance).hasSize(67)
        assertThat(requirements.values).allMatch { it.requiredText("status") == "verified" }
        assertThat(acceptance.values).allMatch { it.requiredText("status") == "verified" }
        assertThat(acceptanceToRequirements.keys).containsExactlyInAnyOrderElementsOf(acceptance.keys)
        listOf(requirements, acceptance, projections, buildSlices, evidence).forEach { records ->
            records.forEach { (id, record) ->
                record.text("status")?.let { status ->
                    assertThat(status)
                        .describedAs("$id status")
                        .isIn(allowedStatuses)
                }
            }
        }

        requirements.forEach { (id, record) ->
            assertReferences(id, "acceptance_ids", record.ids("acceptance_ids"), acceptance)
            assertSource(repositoryRoot, id, record.requiredText("source"))
        }
        acceptance.forEach { (id, record) ->
            assertReferences(id, "projection_ids", record.ids("projection_ids"), projections)
            assertReferences(id, "evidence_ids", record.ids("evidence_ids"), evidence)
            assertSource(repositoryRoot, id, record.requiredText("source"))
            if (record.requiredText("status") == "verified") {
                val evidenceIds = record.ids("evidence_ids")
                assertThat(evidenceIds).describedAs("$id verified evidence").isNotEmpty()
                evidenceIds.forEach { evidenceId ->
                    assertThat(evidence.getValue(evidenceId).requiredText("status"))
                        .describedAs("$id -> $evidenceId")
                        .isEqualTo("verified")
                }
            }
            val expectedRequirements = requirements
                .filterValues { id in it.ids("acceptance_ids") }
                .keys
            assertThat(acceptanceToRequirements.getValue(id))
                .describedAs("$id reverse requirement mapping")
                .containsExactlyInAnyOrderElementsOf(expectedRequirements)
        }
        projections.forEach { (id, record) ->
            assertReferences(id, "business_requirement_ids", record.ids("business_requirement_ids"), requirements)
            assertReferences(id, "acceptance_ids", record.ids("acceptance_ids"), acceptance)
            assertReferences(id, "regression_acceptance_ids", record.ids("regression_acceptance_ids"), acceptance)
            assertReferences(id, "evidence_ids", record.ids("evidence_ids"), evidence)
            assertReferences(id, "depends_on", record.ids("depends_on"), projections)
            assertSource(repositoryRoot, id, record.requiredText("source"))
        }
        buildSlices.forEach { (id, record) ->
            val directAcceptance = record.ids("acceptance_ids") + record.ids("incremental_acceptance_ids")
            val regressionAcceptance = record.ids("regression_acceptance_ids")
            val evidenceIds = record.ids("evidence_ids")
            assertReferences(id, "acceptance_ids", directAcceptance, acceptance)
            assertReferences(id, "regression_acceptance_ids", regressionAcceptance, acceptance)
            assertReferences(id, "evidence_ids", evidenceIds, evidence)
            if (record.requiredText("status") == "verified") {
                (directAcceptance + regressionAcceptance).forEach { acceptanceId ->
                    assertThat(acceptance.getValue(acceptanceId).requiredText("status"))
                        .describedAs("$id -> $acceptanceId")
                        .isEqualTo("verified")
                }
                evidenceIds.forEach { evidenceId ->
                    assertThat(evidence.getValue(evidenceId).requiredText("status"))
                        .describedAs("$id -> $evidenceId")
                        .isEqualTo("verified")
                }
            }
        }
        evidence.forEach { (id, record) ->
            val status = record.requiredText("status")
            val path = record.requiredText("path")
            if (status == "not-built") {
                assertThat(path).describedAs("$id not-built path").isEqualTo("planned")
            }
            if (status == "verified") {
                assertThat(path).describedAs("$id verified path").isNotEqualTo("planned")
                record.text("command")?.let { command ->
                    assertThat(command).describedAs("$id command").isNotBlank()
                }
                assertThat(record.requiredText("result")).describedAs("$id result").isNotBlank()
                assertEvidencePath(repositoryRoot, id, path)
                record.ids("related_paths").forEach { relatedPath ->
                    assertEvidencePath(repositoryRoot, id, relatedPath)
                }
            }
        }

        acceptance.filterValues { it.requiredText("status") == "verified" }.forEach { (acceptanceId, record) ->
            record.ids("projection_ids").forEach { projectionId ->
                val projection = projections.getValue(projectionId)
                val projectedAcceptance = projection.ids("acceptance_ids") + projection.ids("regression_acceptance_ids")
                if (projectedAcceptance.isNotEmpty()) {
                    assertThat(projectedAcceptance)
                        .describedAs("$acceptanceId <-> $projectionId")
                        .contains(acceptanceId)
                }
            }
        }

        mapOf(
            "PAY-EV-010" to "verified",
            "PAY-EV-025" to "verified",
            "PAY-EV-026" to "not-built",
        ).forEach { (id, expected) -> assertThat(evidence.getValue(id).requiredText("status")).isEqualTo(expected) }
        listOf("PAY-CP-001", "PAY-CP-002", "PAY-CP-005", "PAY-CP-006", "PAY-CP-011").forEach { id ->
            assertThat(projections.getValue(id).requiredText("status")).isEqualTo("verified")
        }
        listOf("PAY-CP-012", "PAY-CP-013", "PAY-CP-014", "PAY-CP-015", "PAY-CP-016").forEach { id ->
            assertThat(projections.getValue(id).requiredText("status")).isEqualTo("planned")
        }

        acceptance.forEach { (id, record) ->
            assertThat(record.ids("evidence_ids"))
                .describedAs("$id process-out HTTP evidence")
                .contains("PAY-EV-037")
        }
        val perScenarioHttp = evidence.getValue("PAY-EV-037").records("scenario_evidence")
        assertThat(perScenarioHttp.keys).containsExactlyInAnyOrderElementsOf(acceptance.keys)
        perScenarioHttp.forEach { (id, record) ->
            assertThat(record.keys)
                .describedAs("$id final HTTP evidence fields")
                .containsExactlyInAnyOrder("status", "path", "sha256", "build_revision")
        }

        val compositionEvidence = evidence.getValue("PAY-EV-027")
        assertThat(compositionEvidence.requiredText("status")).isEqualTo("verified")
        assertThat(compositionEvidence.requiredText("path"))
            .isEqualTo("start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt")
        assertThat(compositionEvidence.requiredText("command")).isNotBlank()
        assertThat(compositionEvidence.requiredText("result")).contains("1 composition test / 0 failures")
        assertThat(acceptance.getValue("PAY-AC-083").ids("evidence_ids")).contains("PAY-EV-027")
        assertThat(acceptance.getValue("PAY-AC-082").ids("evidence_ids")).doesNotContain("PAY-EV-027")

        assertThat(buildSlices.getValue("PAYMENT_TIMEOUT_CONFLICT_REVIEW").requiredText("composition_audit"))
            .isEqualTo("completed-by-final-composition-audit")
        val finalComposition = buildSlices.getValue("FINAL_COMPOSITION_AUDIT")
        assertThat(finalComposition.requiredText("issue"))
            .isEqualTo("https://github.com/LDmoxeii/cap4k-reference-payment/issues/8")
        assertThat(finalComposition.requiredText("status")).isEqualTo("verified")
        assertThat(finalComposition.ids("evidence_ids"))
            .containsExactly("PAY-EV-027", "PAY-EV-034", "PAY-EV-035", "PAY-EV-036")
        assertThat(finalComposition.requiredText("published_coordinate_cold_start"))
            .isEqualTo("deferred-to-B6-release-gate")
        assertThat(finalComposition.stringMap("accepted_lineage"))
            .containsExactlyEntriesOf(
                linkedMapOf(
                    "B1" to "6a40c5da2b2057e310c97989d3889d0ce125d06e",
                    "B2" to "43a598285713e1fdace4be2cc501f80d10e5cec0",
                    "B3" to "8750a4b75346eecc33bd1db444d3267455c96ad8",
                    "B4" to "4e347650f8bb2cb9cf0e0adb1c0dc2db89774a15",
                    "B5" to "3fd59cda87e3f2430fea88092a08e1b1939936bb",
                    "PAYMENT_TIMEOUT_CONFLICT_REVIEW" to "e702e725674c4ab1271441cf1ed011bad3b75021",
                ),
            )
    }

    private fun assertPrefixes(records: Map<String, Map<String, Any?>>, prefix: String) {
        assertThat(records.keys).allMatch { it.startsWith(prefix) }
    }

    private fun assertReferences(
        owner: String,
        field: String,
        references: List<String>,
        target: Map<String, Map<String, Any?>>,
    ) {
        references.forEach { reference ->
            assertThat(target).describedAs("$owner.$field -> $reference").containsKey(reference)
        }
    }

    private fun assertSource(repositoryRoot: Path, owner: String, source: String) {
        val parts = source.split("#", limit = 2)
        val sourcePath = repositoryRoot.resolve(parts[0]).normalize()
        assertThat(sourcePath).describedAs("$owner source path").exists()
        if (parts.size == 2) {
            val contents = Files.readString(sourcePath)
            assertThat(contents)
                .describedAs("$owner source anchor ${parts[1]}")
                .contains("id=\"${parts[1]}\"")
        }
    }

    private fun assertEvidencePath(repositoryRoot: Path, owner: String, relativePath: String) {
        assertThat(relativePath).describedAs("$owner portable path").doesNotMatch("^[A-Za-z]:[\\\\/].*")
        if (!relativePath.startsWith("build/")) {
            assertThat(repositoryRoot.resolve(relativePath).normalize())
                .describedAs("$owner evidence path")
                .exists()
        }
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.exists(it.resolve("docs/requirements/traceability.yaml")) }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.records(name: String): Map<String, Map<String, Any?>> =
        (get(name) as? Map<*, *>)
            ?.entries
            ?.associate { (key, value) -> key.toString() to (value as Map<String, Any?>) }
            ?: error("missing traceability section $name")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.idLists(name: String): Map<String, List<String>> =
        (get(name) as? Map<*, *>)
            ?.entries
            ?.associate { (key, value) ->
                key.toString() to when (value) {
                    is List<*> -> value.map { it.toString() }
                    null -> emptyList()
                    else -> listOf(value.toString())
                }
            }
            ?: error("missing traceability section $name")

    private fun Map<String, Any?>.requiredText(name: String): String =
        text(name)?.takeIf { it.isNotBlank() } ?: error("missing non-blank $name in $this")

    private fun Map<String, Any?>.text(name: String): String? = get(name)?.toString()

    private fun Map<String, Any?>.ids(name: String): List<String> = when (val value = get(name)) {
        null -> emptyList()
        is List<*> -> value.map { it.toString() }
        else -> listOf(value.toString())
    }

    private fun Map<String, Any?>.stringMap(name: String): Map<String, String> =
        (get(name) as? Map<*, *>)
            ?.entries
            ?.associate { (key, value) -> key.toString() to value.toString() }
            ?: error("missing map $name in $this")
}
