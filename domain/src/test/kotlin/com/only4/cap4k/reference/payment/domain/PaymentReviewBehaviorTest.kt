package com.only4.cap4k.reference.payment.domain

import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentReviewException
import com.only4.cap4k.reference.payment.domain.aggregates.payment.adjudicateReview
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewDecisionType
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewEligibilityImpact
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PaymentReviewBehaviorTest {
    @Test
    fun `authorized decision retains responsibility facts and duplicate identity cannot rewrite them`() {
        val payment = closedPaymentWithLateSuccess()
        val review = payment.reviewCases.single()
        val time = LocalDateTime.parse("2026-09-23T10:00:00")
        fun adjudicate(
            actor: String = "reference-payment-reviewer",
            reason: String = "keep terminal",
            evidence: String = "ticket://review/1",
            at: LocalDateTime = time,
            authorized: Boolean = true,
        ) = payment.adjudicateReview(
            reviewIdentity = review.reviewIdentity,
            decisionIdentity = "decision-1",
            decision = PaymentReviewDecisionType.KEEP_CURRENT_TERMINAL,
            operatorIdentity = actor,
            operatorRole = "PAYMENT_REVIEW_OPERATOR",
            authorized = authorized,
            reason = reason,
            evidence = evidence,
            decidedAt = at,
            eligibilityImpact = PaymentReviewEligibilityImpact.ALLOW_SETTLEMENT,
            remediationReference = null,
        )

        assertThatThrownBy { adjudicate(authorized = false) }
            .isInstanceOf(PaymentReviewException::class.java)
            .extracting("code").isEqualTo("REVIEW_UNAUTHORIZED")
        assertThat(review.paymentReviewDecisions).isEmpty()

        assertThat(adjudicate().decisionCount).isEqualTo(1)
        val saved = review.paymentReviewDecisions.single()
        assertThat(saved.operatorIdentity).isEqualTo("reference-payment-reviewer")
        assertThat(saved.operatorRole).isEqualTo("PAYMENT_REVIEW_OPERATOR")
        assertThat(saved.authorizationOutcome).isTrue()
        assertThat(saved.reason).isEqualTo("keep terminal")
        assertThat(saved.evidence).isEqualTo("ticket://review/1")
        assertThat(saved.decidedAt).isEqualTo(time)
        assertThat(adjudicate().decisionCount).isEqualTo(1)
        listOf<(Unit) -> Unit>(
            { adjudicate(actor = "other-reviewer") },
            { adjudicate(reason = "changed") },
            { adjudicate(evidence = "ticket://review/2") },
            { adjudicate(at = time.plusSeconds(1)) },
        ).forEach { changed ->
            assertThatThrownBy { changed(Unit) }
                .isInstanceOf(PaymentReviewException::class.java)
                .extracting("code").isEqualTo("REVIEW_DECISION_IDEMPOTENCY_CONFLICT")
        }
        assertThat(review.paymentReviewDecisions).hasSize(1)
        assertThat(review.paymentReviewDecisions.single()).isSameAs(saved)
        assertThat(payment.merchantSuccessNotificationIntentCount).isEqualTo(1)
    }

    private fun closedPaymentWithLateSuccess(): Payment {
        val payment = Payment(
            merchantId = "M-001",
            merchantOrderNumber = "O-REVIEW",
            idempotencyKey = "K-REVIEW",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            paymentMethod = "CARD",
            status = PaymentStatus.CLOSED,
            expiresAt = LocalDateTime.parse("2026-09-22T00:00:00"),
            reservedRefundAmount = BigDecimal.ZERO,
            successfulRefundAmount = BigDecimal.ZERO,
        ).also { it.id = PaymentId.parse("018f22a0-0000-7000-8000-000000000030") }
        val attempt = PaymentAttempt(
            channelId = "C-001",
            channelConfigurationId = "cfg",
            channelConfigurationSnapshot = "snapshot",
            requestIdentity = "attempt-review",
            status = PaymentAttemptStatus.FAILED,
            initiatedAt = LocalDateTime.parse("2026-09-21T00:00:00"),
            finalResult = PaymentAttemptFinalResult.FAILED,
        ).also {
            it.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000031")
            payment.attempts.add(it)
        }
        payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-REVIEW",
            channelTransactionId = "CT-REVIEW",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "SUCCESS",
            occurredAt = LocalDateTime.parse("2026-09-22T01:00:00"),
            receivedAt = LocalDateTime.parse("2026-09-22T01:00:01"),
            verified = true,
            verificationSummary = "verified",
        )
        return payment
    }
}
