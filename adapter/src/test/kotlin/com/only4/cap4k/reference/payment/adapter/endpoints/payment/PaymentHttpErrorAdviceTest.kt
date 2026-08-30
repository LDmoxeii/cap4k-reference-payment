package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import jakarta.persistence.PersistenceException
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentHttpErrorAdviceTest {
    private val advice = PaymentHttpErrorAdvice()

    @Test
    @DisplayName("PAY-AC-003/006/012/017 — HTTP code/status 稳定且 message 安全")
    fun `application errors preserve stable code while exposing Chinese message and safe details`() {
        val paymentId = PaymentId.parse("018f22a0-0000-7000-8000-000000000404")
        val response = advice.notFound(PaymentNotFoundException(paymentId))
        val body = requireNotNull(response.body)

        assertEquals(404, body.status)
        assertEquals("PAYMENT_NOT_FOUND", body.code)
        assertTrue(body.message.contains("未找到支付单"))
        assertEquals(paymentId, body.details["paymentId"])
    }

    @Test
    @DisplayName("PAY-AC-003/006/017 — 原始英文诊断不泄漏到 HTTP")
    fun `uncontrolled argument and state messages never expose raw English diagnostics`() {
        val badRequest = requireNotNull(advice.badRequest(IllegalArgumentException("raw provider failure")).body)
        val conflict = requireNotNull(advice.stateConflict(IllegalStateException("internal state dump")).body)

        assertEquals("INVALID_REQUEST", badRequest.code)
        assertTrue(badRequest.message.contains("请求参数不合法"))
        assertFalse(badRequest.message.contains("raw provider failure"))
        assertEquals("PAYMENT_STATE_CONFLICT", conflict.code)
        assertTrue(conflict.message.contains("当前业务状态"))
        assertFalse(conflict.message.contains("internal state dump"))
    }

    @Test
    @DisplayName("PAY-AC-024/088 — 持久化与未知异常返回固定安全消息")
    fun `persistence and unknown failures return fixed Chinese messages without internal details`() {
        val persistence = requireNotNull(
            advice.concurrentModification(PersistenceException("SQLState 23505 unique constraint payment_order")).body
        )
        val unknown = requireNotNull(advice.internalError(RuntimeException("provider stack and secret")).body)

        assertEquals("CONCURRENT_MODIFICATION", persistence.code)
        assertTrue(persistence.message.contains("其他请求修改"))
        assertFalse(persistence.message.contains("SQLState"))
        assertTrue(persistence.details.isEmpty())

        assertEquals(500, unknown.status)
        assertEquals("INTERNAL_ERROR", unknown.code)
        assertTrue(unknown.message.contains("系统处理请求时发生异常"))
        assertFalse(unknown.message.contains("provider"))
        assertTrue(unknown.details.isEmpty())
    }
}
