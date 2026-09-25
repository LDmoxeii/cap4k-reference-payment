package com.only4.cap4k.reference.payment.adapter.reference

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Reference profile 的人工责任边界。HTTP 只携带 fixture/session alias；业务 actorId 和角色
 * 永远由该服务端 registry 解析，不能由请求体声明。
 */
data class ReferenceActorContext(
    val actorId: String,
    val role: String,
)

class ReferenceActorContextException(message: String) : RuntimeException(message)

@Component
class ReferenceActorRegistry {
    private val contexts = ConcurrentHashMap(DEFAULT_CONTEXTS)

    fun resolve(alias: String?): ReferenceActorContext {
        val normalized = alias?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw ReferenceActorContextException("缺少可信 ReferenceActorContext")
        return contexts[normalized]
            ?: throw ReferenceActorContextException("未知的 ReferenceActorContext")
    }

    /** Reference fixture/session setup 只能通过 alias 注册不可变上下文。 */
    fun register(alias: String, actorId: String, role: String) {
        require(alias.isNotBlank()) { "ReferenceActorContext alias 不能为空" }
        require(actorId.isNotBlank()) { "Reference actorId 不能为空" }
        require(role.isNotBlank()) { "Reference actor role 不能为空" }
        contexts[alias.trim()] = ReferenceActorContext(actorId.trim(), role.trim().uppercase())
    }

    companion object {
        const val PAYMENT_REVIEWER_ALIAS = "fixture-payment-reviewer"
        const val REFUND_REVIEWER_ALIAS = "fixture-refund-reviewer"
        const val RECONCILIATION_OPERATOR_ALIAS = "fixture-reconciliation-operator"
        const val SETTLEMENT_OPERATOR_ALIAS = "fixture-settlement-operator"
        const val SETTLEMENT_REVIEWER_ALIAS = "fixture-settlement-reviewer"

        private val DEFAULT_CONTEXTS = mapOf(
            PAYMENT_REVIEWER_ALIAS to ReferenceActorContext("reference-payment-reviewer", "PAYMENT_REVIEW_OPERATOR"),
            REFUND_REVIEWER_ALIAS to ReferenceActorContext("reference-refund-reviewer", "REFUND_REVIEW_OPERATOR"),
            RECONCILIATION_OPERATOR_ALIAS to ReferenceActorContext("reference-reconciliation-operator", "RECONCILIATION_OPERATOR"),
            SETTLEMENT_OPERATOR_ALIAS to ReferenceActorContext("reference-settlement-operator", "SETTLEMENT_OPERATOR"),
            SETTLEMENT_REVIEWER_ALIAS to ReferenceActorContext("reference-settlement-reviewer", "SETTLEMENT_OPERATOR"),
        )
    }
}
