package com.only4.cap4k.reference.payment.adapter.reference

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Reference-only control of payment-channel submission behaviour.  The selected script affects
 * submission acceptance only; the later payment callback still owns SUCCESS/FAILURE/UNKNOWN.
 */
@Component
class ReferencePaymentChannelScriptRegistry {
    enum class Script {
        REJECT_ON_SUBMIT,
        ACCEPT_THEN_SUCCESS,
        ACCEPT_THEN_FAILURE,
        ACCEPT_THEN_UNKNOWN,
        NO_RESULT,
    }

    private val scripts = ConcurrentHashMap<String, Script>()

    fun scriptFor(channelId: String): Script = scripts[channelId.trim()] ?: DEFAULT

    fun configure(channelId: String, script: Script): Script {
        require(channelId.isNotBlank()) { "reference payment channelId 不能为空" }
        scripts[channelId.trim()] = script
        return script
    }

    fun reset(channelId: String): Script {
        require(channelId.isNotBlank()) { "reference payment channelId 不能为空" }
        scripts.remove(channelId.trim())
        return scriptFor(channelId)
    }

    companion object {
        val DEFAULT: Script = Script.ACCEPT_THEN_SUCCESS
    }
}
