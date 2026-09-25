package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.reference.payment.adapter.endpoints.payment.PaymentHttpErrorAdvice
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceConflictException
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceRegistry
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.RegisterReferenceCallbackEvidenceEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterReferenceCallbackEvidenceEndpointHandlerTest {
    private val handler = RegisterReferenceCallbackEvidenceEndpointHandler(ReferenceCallbackEvidenceRegistry())

    @Test
    fun `same fixture idempotency key and payload replays the original evidence registration`() {
        val first = handler.handle(validRequest())
        val replay = handler.handle(validRequest())

        assertFalse(first.idempotentReplay)
        assertTrue(replay.idempotentReplay)
        assertEquals(first.evidenceId, replay.evidenceId)
        assertEquals("PAYMENT", replay.kind)
    }

    @Test
    fun `different payload under same fixture idempotency key is stable API conflict`() {
        handler.handle(validRequest())
        val conflict = assertFailsWith<ReferenceCallbackEvidenceConflictException> {
            handler.handle(validRequest().copy(rawPayload = "different-raw-payload"))
        }

        val response = PaymentHttpErrorAdvice().referenceFixtureConflict(conflict)
        assertEquals(409, response.statusCode.value())
        assertEquals("IDEMPOTENCY_CONFLICT", requireNotNull(response.body).code)
    }

    @Test
    fun `invalid kind and missing evidence field produce validation errors before registration`() {
        val invalidKind = assertFailsWith<IllegalArgumentException> {
            handler.handle(validRequest().copy(kind = "card-verdict"))
        }
        val missingPayload = assertFailsWith<IllegalArgumentException> {
            handler.handle(validRequest().copy(rawPayload = null))
        }

        val advice = PaymentHttpErrorAdvice()
        assertEquals("VALIDATION_ERROR", requireNotNull(advice.badRequest(invalidKind).body).code)
        assertEquals("VALIDATION_ERROR", requireNotNull(advice.badRequest(missingPayload).body).code)
    }

    private fun validRequest() = RegisterReferenceCallbackEvidenceEndpoint.Request(
        idempotencyKey = "fixture-evidence-1",
        kind = "payment",
        channelId = "sandbox-payment",
        externalIdentity = "payment-notification-1",
        associationIdentity = "pay-1|attempt-1|txn-1",
        money = Money(currency = "CNY", amountMinor = "1234"),
        rawPayload = "payment-raw",
    )
}
