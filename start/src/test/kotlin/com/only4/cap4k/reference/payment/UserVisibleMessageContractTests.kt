package com.only4.cap4k.reference.payment

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserVisibleMessageContractTests {
    @Test
    fun `user visible messages are Chinese and provider raw causes are never projected`() {
        val root = repositoryRoot()
        val sourceRoots = listOf("domain/src/main", "application/src/main", "adapter/src/main", "start/src/main")
        val sources = sourceRoots.flatMap { relative ->
            Files.walk(root.resolve(relative)).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .map { it to Files.readString(it) }
                    .toList()
            }
        }

        val allSource = sources.joinToString("\n") { (path, text) -> "// $path\n$text" }
        assertThat(allSource)
            .doesNotContain("error.message ?: error::class.simpleName")
            .doesNotContain("failure.message ?: failure::class.simpleName")
            .doesNotContain("channel gateway failed:")

        val gatewayCommands = listOf(
            root.resolve("application/src/main/kotlin/com/only4/cap4k/reference/payment/application/commands/payment/attempt/StartPaymentAttemptCmd.kt"),
            root.resolve("application/src/main/kotlin/com/only4/cap4k/reference/payment/application/commands/refund/create/CreateRefundCmd.kt"),
        ).associateWith(Files::readString)
        gatewayCommands.forEach { (path, source) ->
            assertThat(source)
                .describedAs("$path 不得把 provider diagnostic 直接传入用户可见或持久化 sink")
                .doesNotContain("diagnosticSummary = gateway.diagnosticSummary")
                .doesNotContain("gateway.failureCode ?: \"CHANNEL_REJECTED\",\n                    gateway.diagnosticSummary")
                .doesNotContain("listOfNotNull(\n                        gateway.failureCode ?: \"CHANNEL_REJECTED\",\n                        gateway.diagnosticSummary")
            assertThat(Regex("gateway\\.diagnosticSummary").findAll(source).count())
                .describedAs("$path 中 raw provider diagnostic 只允许出现于日志隔离点")
                .isEqualTo(1)
            assertThat(source).contains("原始诊断仅记录日志")
            assertThat(source).contains("safeDiagnostic")
        }
        val userVisibleAssignment = Regex(
            """(?:message|diagnosticSummary|rejectionSummary|conflictSummary|blockingReason|lastReviewSummary|failureSummary)\s*=\s*"([^"\n]*)""""
        )
        val violations = sources.flatMap { (path, text) ->
            userVisibleAssignment.findAll(text).mapNotNull { match ->
                val value = match.groupValues[1]
                val containsChinese = value.any { it in '\u4e00'..'\u9fff' }
                if (containsChinese) null else "$path: $value"
            }.toList()
        }
        assertThat(violations)
            .describedAs("用户可见 message/summary 字面量必须包含中文；机器 code、enum、identity 和 raw 字段不在本扫描范围")
            .isEmpty()
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.exists(it.resolve("docs/requirements/traceability.yaml")) }
}
