package com.only4.cap4k.reference.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatabaseSchemaCommentContractTests {
    private val expectedTables = setOf(
        "payment",
        "payment_attempt",
        "payment_notification_receipt",
        "payment_review_case",
        "payment_review_decision",
        "refund",
        "refund_attempt",
        "refund_notification_receipt",
        "merchant_channel_configuration",
        "reconciliation_batch",
        "reconciliation_run",
        "reconciliation_item",
        "reconciliation_disposition",
        "reconciliation_confirmation_fact",
        "merchant_settlement",
        "settlement_line",
        "settlement_execution_attempt",
        "settlement_result_receipt",
    )

    @Test
    fun `design and runtime schema keep readable table and column comments aligned`() {
        val root = repositoryRoot()
        val design = Files.readString(root.resolve("design/schema.sql"))
        val runtime = Files.readString(root.resolve("start/src/main/resources/schema.sql"))

        val designTables = tableComments(design)
        val runtimeTables = tableComments(runtime)
        assertThat(designTables.keys).containsExactlyInAnyOrderElementsOf(expectedTables)
        assertThat(runtimeTables).containsExactlyInAnyOrderEntriesOf(designTables)
        assertThat(designTables.values).allSatisfy { assertThat(it).matches(".*[\\u4e00-\\u9fff].*") }

        val designColumns = designColumnComments(design)
        val runtimeColumns = runtimeColumnComments(runtime)
        assertThat(designColumns).isNotEmpty
        assertThat(runtimeColumns).containsExactlyInAnyOrderEntriesOf(designColumns)
        assertThat(designColumns.keys.map { it.substringBefore('.') }.toSet())
            .containsExactlyInAnyOrderElementsOf(expectedTables)
        assertThat(designColumns.values).allSatisfy { assertThat(it).matches(".*[\\u4e00-\\u9fff].*") }
    }

    @Test
    fun `cap4k machine annotations remain present in design schema`() {
        val design = Files.readString(repositoryRoot().resolve("design/schema.sql"))
        assertThat(design.countOccurrences("@Managed")).isGreaterThanOrEqualTo(108)
        assertThat(design.countOccurrences("@Type")).isGreaterThanOrEqualTo(32)
        assertThat(design.countOccurrences("@ParentRef")).isGreaterThanOrEqualTo(13)
        assertThat(design.countOccurrences("@Parent=")).isGreaterThanOrEqualTo(13)
        assertThat(design.countOccurrences("@RefAggregate")).isGreaterThanOrEqualTo(4)
        assertThat(design).doesNotContain("comment '@Managed")
            .doesNotContain("comment '@Type")
            .doesNotContain("is '@Parent=")
    }

    @Test
    fun `value object and design fields have compact Chinese descriptions without changing source structure`() {
        val mapper = ObjectMapper()
        val valueObjectsPath = repositoryRoot().resolve("design/value-objects.json")
        val designPath = repositoryRoot().resolve("design/design.json")
        val valueObjectsText = Files.readString(valueObjectsPath)
        val designText = Files.readString(designPath)
        assertThat(valueObjectsText.trim()).doesNotContain("\n").doesNotContain("\r")
        assertThat(designText.trim()).doesNotContain("\n").doesNotContain("\r")

        val valueObjects = mapper.readTree(valueObjectsText)
        val designEntries = mapper.readTree(designText)
        assertThat(valueObjects.isArray).isTrue
        assertThat(designEntries.isArray).isTrue
        assertThat(valueObjects.size()).isEqualTo(11)
        assertThat(designEntries.size()).isEqualTo(57)

        val valueObjectFields = jsonFields(valueObjects)
        val designFields = jsonFields(designEntries)
        assertThat(valueObjectFields).hasSize(112)
        assertThat(designFields).hasSize(1188)
        assertJsonFieldDescriptions(valueObjectFields)
        assertJsonFieldDescriptions(designFields)

        assertThat(structureHash(valueObjects)).isEqualTo(
            "e808317524f0ee11da7cd820f6fdebdc39ed6e34d301e42f1cd7436bb30293fe",
        )
        assertThat(structureHash(designEntries)).isEqualTo(
            "114e7001046d1e395abddc86df10f112b23f045522203d8c02fb7770d76ec8a1",
        )
    }

    @Test
    fun `schema comment guide explains design source runtime projection and machine metadata`() {
        val guide = Files.readString(
            repositoryRoot().resolve("docs/requirements/acceptance/payment-acceptance-guide.md"),
        )
        assertThat(guide)
            .contains("design/schema.sql")
            .contains("start/src/main/resources/schema.sql")
            .contains("@Managed")
            .contains("@Type")
            .contains("COMMENT")
            .contains("不是生产迁移脚本")
    }

    private fun jsonFields(root: JsonNode): List<JsonNode> {
        val fields = mutableListOf<JsonNode>()
        root.forEach { entry ->
            listOf("fields", "resultFields").forEach { key ->
                entry.get(key)?.takeIf { it.isArray }?.forEach(fields::add)
            }
        }
        return fields
    }

    private fun assertJsonFieldDescriptions(fields: List<JsonNode>) {
        fields.forEach { field ->
            val keys = field.fieldNames().asSequence().toList()
            assertThat(
                keys == listOf("name", "type", "description") ||
                    keys == listOf("name", "type", "defaultValue", "description"),
            ).isTrue
            assertThat(field.path("name").asText()).isNotBlank
            assertThat(field.path("type").asText()).isNotBlank
            assertThat(field.path("description").asText()).matches(".*[\\u4e00-\\u9fff].*")
            assertThat(field.path("description").asText()).doesNotContain("\n", "\r")
        }
    }

    private fun structureHash(root: JsonNode): String {
        val copy = root.deepCopy<JsonNode>()
        copy.forEach { entry ->
            listOf("fields", "resultFields").forEach { key ->
                entry.get(key)?.takeIf { it.isArray }?.forEach { field ->
                    (field as? com.fasterxml.jackson.databind.node.ObjectNode)?.remove("description")
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(ObjectMapper().writeValueAsBytes(copy))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun tableComments(sql: String): Map<String, String> =
        Regex("(?m)^comment on table (\\w+) is '([^']*)';\\s*$")
            .findAll(sql)
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun designColumnComments(sql: String): Map<String, String> =
        Regex("(?m)^[ \\t]+(\\w+)[ \\t]+.*comment '([^']*)'[,]?[ \\t]*$")
            .findAll(sql)
            .filterNot { it.groupValues[1].equals("constraint", ignoreCase = true) }
            .map { match ->
                val table = Regex("create table (\\w+) \\(").findAll(sql.substring(0, match.range.first)).last().groupValues[1]
                "$table.${match.groupValues[1]}" to match.groupValues[2]
            }
            .toMap()

    private fun runtimeColumnComments(sql: String): Map<String, String> =
        Regex("(?m)^comment on column (\\w+)\\.(\\w+) is '([^']*)';\\s*$")
            .findAll(sql)
            .associate { "${it.groupValues[1]}.${it.groupValues[2]}" to it.groupValues[3] }

    private fun String.countOccurrences(value: String): Int = windowed(value.length, 1, partialWindows = false).count { it == value }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.exists(it.resolve("docs/requirements/traceability.yaml")) }
}

