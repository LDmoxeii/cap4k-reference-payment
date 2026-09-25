package com.only4.cap4k.reference.payment.adapter.reference

import org.springframework.stereotype.Component
/**
 * CAP4K binding resolves the header alias, then the endpoint handler consumes the immutable
 * context immediately before command dispatch. The one-shot carrier avoids exposing actorId in
 * the HTTP body and is cleared on consume.
 */
@Component
class ReferenceActorContextResolver(
    private val registry: ReferenceActorRegistry,
) {
    private val current = ThreadLocal<ReferenceActorContext>()

    fun bind(alias: String?) {
        // An endpoint mapper can be reused by a servlet worker thread.  Clear any prior one-shot
        // value before resolving the next header so a malformed/missing alias can never inherit an
        // actor from an earlier request on that worker.
        current.remove()
        current.set(registry.resolve(alias))
    }

    fun consume(): ReferenceActorContext {
        val context = current.get()
            ?: throw ReferenceActorContextException("当前调用缺少可信 ReferenceActorContext")
        current.remove()
        return context
    }

    companion object {
        const val HEADER_NAME = "X-Reference-Actor-Context"
    }
}
