package com.only4.cap4k.reference.payment.adapter.reference

import com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.result.VerifySettlementResultHandler
import com.only4.cap4k.reference.payment.adapter.application.capabilities.payment.channel.VerifyPaymentResultHandler
import com.only4.cap4k.reference.payment.adapter.application.capabilities.refund.channel.VerifyRefundResultHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.result.VerifySettlementResult
import com.only4.cap4k.reference.payment.application.capabilities.payment.channel.VerifyPaymentResult
import com.only4.cap4k.reference.payment.application.capabilities.refund.channel.VerifyRefundResult
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferenceCallbackEvidenceRegistryTest {
    @Test
    fun `payment refund and settlement verifiers accept only pre-registered server evidence`() {
        val registry = ReferenceCallbackEvidenceRegistry()

        val payment = paymentInput(payload = "payment-raw")
        registry.register(payment, "payment-fixture-key")
        assertTrue(
            VerifyPaymentResultHandler(registry).call(
                VerifyPaymentResult.Request(
                    channelId = payment.channelId,
                    notificationId = payment.externalIdentity,
                    payload = payment.canonicalPayload,
                    paymentId = "pay-1",
                    paymentAttemptId = "attempt-1",
                    channelTransactionId = "txn-1",
                    amount = payment.amount,
                    currency = payment.currency,
                ),
            ).verified,
        )
        assertFalse(
            VerifyPaymentResultHandler(registry).call(
                VerifyPaymentResult.Request(
                    channelId = payment.channelId,
                    notificationId = payment.externalIdentity,
                    payload = "forged-raw-payload",
                    paymentId = "pay-1",
                    paymentAttemptId = "attempt-1",
                    channelTransactionId = "txn-1",
                    amount = payment.amount,
                    currency = payment.currency,
                ),
            ).verified,
        )

        val refund = ReferenceCallbackEvidenceInput(
            kind = ReferenceCallbackKind.REFUND,
            channelId = "sandbox-refund",
            externalIdentity = "refund-notification-1",
            associationIdentity = "refund-1|refund-attempt-1|channel-refund-1",
            amount = BigDecimal("12.34"),
            currency = "cny",
            canonicalPayload = "refund-raw",
        )
        registry.register(refund, "refund-fixture-key")
        assertTrue(
            VerifyRefundResultHandler(registry).call(
                VerifyRefundResult.Request(
                    channelId = refund.channelId,
                    notificationId = refund.externalIdentity,
                    payload = refund.canonicalPayload,
                    refundId = "refund-1",
                    refundAttemptId = "refund-attempt-1",
                    channelRefundId = "channel-refund-1",
                    amount = refund.amount,
                    currency = refund.currency,
                ),
            ).verified,
        )
        assertFalse(
            VerifyRefundResultHandler(registry).call(
                VerifyRefundResult.Request(
                    channelId = refund.channelId,
                    notificationId = refund.externalIdentity,
                    payload = "forged-refund-raw-payload",
                    refundId = "refund-1",
                    refundAttemptId = "refund-attempt-1",
                    channelRefundId = "channel-refund-1",
                    amount = refund.amount,
                    currency = refund.currency,
                ),
            ).verified,
        )

        val settlementId = MerchantSettlementId.parse("018f22a0-0000-7000-8000-000000000101")
        val settlement = ReferenceCallbackEvidenceInput(
            kind = ReferenceCallbackKind.SETTLEMENT,
            channelId = "sandbox-settlement",
            externalIdentity = "settlement-notification-1",
            associationIdentity = "$settlementId|execution-1|execution-group-1|request-1|external-1",
            amount = BigDecimal("12.34"),
            currency = "CNY",
            canonicalPayload = "settlement-raw",
        )
        registry.register(settlement, "settlement-fixture-key")
        val settlementResponse = VerifySettlementResultHandler(registry).call(
            VerifySettlementResult.Request(
                channelId = settlement.channelId,
                notificationId = settlement.externalIdentity,
                merchantSettlementId = settlementId,
                executionAttemptId = "execution-1",
                executionGroupIdentity = "execution-group-1",
                requestIdentity = "request-1",
                externalSettlementIdentity = "external-1",
                amount = settlement.amount,
                currency = settlement.currency,
                result = "SUCCESS",
                resultCode = null,
                occurredAt = Instant.parse("2026-09-22T00:00:00Z"),
                payload = settlement.canonicalPayload,
            ),
        )
        assertTrue(settlementResponse.verified)
        assertEquals("SUCCESS", settlementResponse.normalizedResult)
        assertFalse(
            VerifySettlementResultHandler(registry).call(
                VerifySettlementResult.Request(
                    channelId = settlement.channelId,
                    notificationId = settlement.externalIdentity,
                    merchantSettlementId = settlementId,
                    executionAttemptId = "execution-1",
                    executionGroupIdentity = "execution-group-1",
                    requestIdentity = "request-1",
                    externalSettlementIdentity = "external-1",
                    amount = settlement.amount,
                    currency = settlement.currency,
                    result = "SUCCESS",
                    resultCode = null,
                    occurredAt = Instant.parse("2026-09-22T00:00:00Z"),
                    payload = "forged-settlement-raw-payload",
                ),
            ).verified,
        )
    }

    private fun paymentInput(payload: String) = ReferenceCallbackEvidenceInput(
        kind = ReferenceCallbackKind.PAYMENT,
        channelId = "sandbox-payment",
        externalIdentity = "payment-notification-1",
        associationIdentity = "pay-1|attempt-1|txn-1",
        amount = BigDecimal("12.34"),
        currency = "CNY",
        canonicalPayload = payload,
    )
}
