package com.only4.cap4k.reference.payment.domain

import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.SettlementFeeRule
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.startAttempt
import com.only4.cap4k.reference.payment.domain.values.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class PaymentBehaviorTest {
    @Test
    fun `money keeps exact cents and rejects invalid payment amounts`() {
        val money = Money.of(BigDecimal("100"), "cny")

        assertThat(money.amount).isEqualByComparingTo("100.00")
        assertThat(money.currency).isEqualTo("CNY")
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal.ZERO, "CNY") }
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal("10.001"), "CNY") }
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal.ONE, "CN") }
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal.ONE, "USD") }
    }

    @Test
    fun `accepted channel result forms success once and later failure cannot roll it back`() {
        val payment = payment()
        val attempt = payment.startAttempt(
            channelId = "C-001",
            channelConfigurationId = "018f22a0-0000-7000-8000-000000000001",
            channelConfigurationSnapshot = "channelId=C-001;currency=CNY",
            requestIdentity = "request-001",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
        )
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000011")

        val success = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-001",
            channelTransactionId = "CT-001",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "SUCCESS",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:01:01"),
            verified = true,
            verificationSummary = "verified",
            settlementFeeRule = feeRule(),
        )
        val duplicate = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-001",
            channelTransactionId = "CT-001",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "SUCCESS",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:02:00"),
            verified = true,
            verificationSummary = "verified",
        )
        val conflict = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-002",
            channelTransactionId = "CT-002",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "FAILED",
            occurredAt = LocalDateTime.parse("2026-08-17T08:03:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:03:01"),
            verified = true,
            verificationSummary = "verified",
        )

        assertThat(success.accepted).isTrue()
        assertThat(success.successFactFormedNow).isTrue()
        assertThat(duplicate.duplicate).isTrue()
        assertThat(duplicate.successFactFormedNow).isFalse()
        assertThat(conflict.accepted).isFalse()
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCEEDED)
        assertThat(payment.successFactFormed).isTrue()
        assertThat(payment.settlementFeeFactIdentity).isEqualTo("payment:${payment.id}:settlement-fee")
        assertThat(payment.settlementFeeBasisPoints).isEqualTo(200)
        assertThat(payment.settlementFixedFeeAmount).isEqualByComparingTo("0")
        assertThat(payment.settlementFeeRoundingMode).isEqualTo("HALF_UP")
        assertThat(payment.settlementFeeCurrencyPrecision).isEqualTo(2)
        assertThat(payment.settlementFeeCalculationAmount).isEqualByComparingTo("100.00")
        assertThat(payment.settlementFeeAmount).isEqualByComparingTo("2.00")
        assertThat(payment.settlementFeeFormedAt).isEqualTo(LocalDateTime.parse("2026-08-17T08:01:00"))
        assertThat(payment.merchantSuccessNotificationIntentCount).isEqualTo(1)
        assertThat(attempt.paymentNotificationReceipts).hasSize(2)
        assertThat(attempt.paymentNotificationReceipts.first().receiveCount).isEqualTo(2)
        assertThat(payment.settlementBlocked).isTrue()
        assertThatIllegalStateException().isThrownBy {
            payment.startAttempt(
                channelId = "C-001",
                channelConfigurationId = "018f22a0-0000-7000-8000-000000000001",
                channelConfigurationSnapshot = "channelId=C-001",
                requestIdentity = "request-002",
                initiatedAt = LocalDateTime.parse("2026-08-17T08:04:00"),
            )
        }
    }


    @Test
    fun `verified channel mismatch is rejected and retained as a notification receipt`() {
        val payment = payment()
        val attempt = payment.startAttempt(
            channelId = "C-001",
            channelConfigurationId = "018f22a0-0000-7000-8000-000000000001",
            channelConfigurationSnapshot = "channelId=C-001;currency=CNY",
            requestIdentity = "request-mismatch",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
        )
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000012")

        val decision = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-999",
            notificationId = "N-CHANNEL-MISMATCH",
            channelTransactionId = "CT-CHANNEL-MISMATCH",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "SUCCESS",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:01:01"),
            verified = true,
            verificationSummary = "signature verified",
        )

        assertThat(decision.accepted).isFalse()
        assertThat(decision.rejectionSummary).contains("does not match attempt channel")
        assertThat(payment.status).isEqualTo(PaymentStatus.PROCESSING)
        assertThat(attempt.paymentNotificationReceipts).hasSize(1)
        assertThat(attempt.paymentNotificationReceipts.single().decision).isEqualTo(ChannelResultDisposition.REJECTED)
        assertThat(attempt.paymentNotificationReceipts.single().rejectionSummary).contains("does not match attempt channel")
    }

    private fun feeRule(): SettlementFeeRule = SettlementFeeRule(
        configurationId = "018f22a0-0000-7000-8000-000000000001",
        basisPoints = 200,
        fixedFeeAmount = BigDecimal.ZERO,
        roundingMode = RoundingMode.HALF_UP,
        currencyPrecision = 2,
    )

    private fun payment(): Payment = Payment(
        merchantId = "M-001",
        merchantOrderNumber = "O-001",
        idempotencyKey = "K-001",
        amount = BigDecimal("100.00"),
        currency = "CNY",
        paymentMethod = "CARD",
        status = PaymentStatus.PENDING,
        expiresAt = LocalDateTime.parse("2030-01-01T00:00:00"),
        reservedRefundAmount = BigDecimal.ZERO,
        successfulRefundAmount = BigDecimal.ZERO,
    ).also {
        it.id = PaymentId.parse("018f22a0-0000-7000-8000-000000000010")
    }
}
