package com.only4.cap4k.reference.payment.adapter.reference

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/** Reference-only executor observations are selected by the caller's stable execution identity. */
@Component
class ReferenceSettlementExecutorScriptRegistry {
    enum class Script { SUCCESS, FAILURE, UNKNOWN, NO_RESULT }

    data class State(
        val executionId: String,
        val script: Script,
        val configured: Boolean,
        val consumed: Boolean,
        val observation: String?,
        val diagnosticSummary: String?,
    )

    private val states = ConcurrentHashMap<String, State>()

    @Synchronized
    fun configure(executionId: String, script: Script): State {
        val id = validId(executionId)
        val existing = states[id]
        if (existing?.consumed == true) return existing
        return State(id, script, true, false, null, null).also { states[id] = it }
    }

    @Synchronized
    fun reset(executionId: String): State {
        val id = validId(executionId)
        val existing = states[id]
        if (existing?.consumed == true) return existing
        states.remove(id)
        return read(id)
    }

    fun read(executionId: String): State {
        val id = validId(executionId)
        return states[id] ?: State(id, Script.NO_RESULT, false, false, null, null)
    }

    @Synchronized
    fun consume(executionId: String): State {
        val id = validId(executionId)
        val existing = read(id)
        if (existing.consumed) return existing
        val diagnostic = when (existing.script) {
            Script.SUCCESS -> "reference executor 返回可信成功结果"
            Script.FAILURE -> "reference executor 返回可信失败结果"
            Script.UNKNOWN -> "reference executor 返回可信未知结果"
            Script.NO_RESULT -> "出款请求已提交，但 executor 未返回业务结果"
        }
        return existing.copy(
            consumed = true,
            observation = existing.script.name,
            diagnosticSummary = diagnostic,
        ).also { states[id] = it }
    }

    private fun validId(executionId: String): String =
        executionId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("reference settlement executionId 不能为空")
}
