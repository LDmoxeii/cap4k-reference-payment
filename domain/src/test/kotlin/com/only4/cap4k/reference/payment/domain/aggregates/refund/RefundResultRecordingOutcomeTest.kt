package com.only4.cap4k.reference.payment.domain.aggregates.refund

import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.values.RefundResultRecordingOutcome
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RefundResultRecordingOutcomeTest {
    @Test
    @DisplayName("PAY-AC-020/029 — 退款成功预算转换与不可回退")
    fun `accepted success outcome exposes a coherent conversion`() {
        val outcome = RefundResultRecordingOutcome(
            refundStatus = RefundStatus.SUCCEEDED,
            attemptStatus = RefundAttemptStatus.SUCCEEDED,
            notificationReceiveCount = 1,
            disposition = RefundResultDisposition.SUCCESS_ACCEPTED,
            reservationReleasedNow = false,
            reservationConvertedToSuccessNow = true,
            reviewRequiredNow = false,
            rejectionSummary = null,
            conflictSummary = null,
        )

        assertThat(outcome.accepted).isTrue()
        assertThat(outcome.duplicate).isFalse()
    }

    @Test
    @DisplayName("PAY-AC-025/029 — 退款结果证据不变量与冲突")
    fun `outcome rejects contradictory or incomplete evidence`() {
        assertThatIllegalArgumentException().isThrownBy {
            RefundResultRecordingOutcome(
                refundStatus = RefundStatus.SUCCEEDED,
                attemptStatus = RefundAttemptStatus.SUCCEEDED,
                notificationReceiveCount = 0,
                disposition = RefundResultDisposition.SUCCESS_ACCEPTED,
                reservationReleasedNow = false,
                reservationConvertedToSuccessNow = true,
                reviewRequiredNow = false,
                rejectionSummary = null,
                conflictSummary = null,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            RefundResultRecordingOutcome(
                refundStatus = RefundStatus.PROCESSING,
                attemptStatus = RefundAttemptStatus.PROCESSING,
                notificationReceiveCount = 1,
                disposition = RefundResultDisposition.ATTEMPT_NOT_FOUND,
                reservationReleasedNow = false,
                reservationConvertedToSuccessNow = false,
                reviewRequiredNow = false,
                rejectionSummary = "missing attempt",
                conflictSummary = null,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            RefundResultRecordingOutcome(
                refundStatus = RefundStatus.PROCESSING,
                attemptStatus = RefundAttemptStatus.PROCESSING,
                notificationReceiveCount = 1,
                disposition = RefundResultDisposition.CONFLICT,
                reservationReleasedNow = false,
                reservationConvertedToSuccessNow = false,
                reviewRequiredNow = false,
                rejectionSummary = null,
                conflictSummary = null,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            RefundResultRecordingOutcome(
                refundStatus = RefundStatus.FAILED,
                attemptStatus = RefundAttemptStatus.FAILED,
                notificationReceiveCount = 1,
                disposition = RefundResultDisposition.FAILURE_ACCEPTED,
                reservationReleasedNow = false,
                reservationConvertedToSuccessNow = true,
                reviewRequiredNow = false,
                rejectionSummary = null,
                conflictSummary = null,
            )
        }
    }

    @Test
    @DisplayName("PAY-AC-099 — 可重试失败终结当前 attempt 但继续占用原预算")
    fun `retryable failure keeps the refund reservation for a new attempt`() {
        val (refund, attempt) = unknownRefund()
        refund.status = RefundStatus.PROCESSING
        attempt.status = RefundAttemptStatus.ACCEPTED

        val outcome = refund.recordChannelResult(
            attemptId = attempt.id,
            channelId = "C-001",
            notificationId = "retryable-failure-1",
            channelRefundId = "channel-refund-1",
            amount = BigDecimal("40.00"),
            currency = "CNY",
            result = "RETRYABLE_FAILURE",
            occurredAt = LocalDateTime.parse("2026-09-22T07:05:00"),
            receivedAt = LocalDateTime.parse("2026-09-22T07:05:01"),
            verified = true,
            verificationSummary = "verified retryable failure",
        )
        val nextAttempt = refund.createAttempt(
            now = LocalDateTime.parse("2026-09-22T07:06:00"),
            channelId = "C-001",
            configurationId = "018f22a0-0000-7000-8000-000000000003",
            snapshot = "channelId=C-001;currency=CNY",
            requestIdentity = "refund-request-002",
            reviewAfterAt = LocalDateTime.parse("2026-09-22T07:11:00"),
        )

        assertThat(outcome.disposition).isEqualTo(RefundResultDisposition.RETRYABLE_FAILURE_ACCEPTED)
        assertThat(outcome.reservationReleasedNow).isFalse()
        assertThat(outcome.reservationConvertedToSuccessNow).isFalse()
        assertThat(refund.reservationActive).isTrue()
        assertThat(refund.status).isEqualTo(RefundStatus.PROCESSING)
        assertThat(attempt.status).isEqualTo(RefundAttemptStatus.FAILED)
        assertThat(attempt.finalResult).isEqualTo(RefundAttemptFinalResult.RETRYABLE_FAILURE)
        assertThat(nextAttempt).isNotSameAs(attempt)
        assertThat(refund.attempts).hasSize(2)
    }

    @Test
    @DisplayName("PAY-AC-025/086/099 — UNKNOWN 退款人工成功只转换一次预算")
    fun `manual success adjudication converts an active reservation once`() {
        val (refund, attempt) = unknownRefund()

        val outcome = refund.adjudicateManualResult(
            attemptId = attempt.id,
            resolutionIdentity = "manual-resolution-success",
            operatorIdentity = "reference-refund-reviewer",
            operatorRole = "REFUND_REVIEW_OPERATOR",
            outcome = "CONFIRM_SUCCESS",
            reason = "渠道补充了最终成功证明",
            evidence = "evidence://refund/success",
            adjudicatedAt = LocalDateTime.parse("2026-09-22T08:00:00"),
        )

        assertThat(outcome.reservationConvertedToSuccessNow).isTrue()
        assertThat(outcome.reservationReleasedNow).isFalse()
        assertThat(refund.status).isEqualTo(RefundStatus.SUCCEEDED)
        assertThat(refund.reservationActive).isFalse()
        assertThat(refund.reservationConvertedToSuccess).isTrue()
        assertThat(refund.reservationReleased).isFalse()
        assertThat(attempt.finalResult).isEqualTo(RefundAttemptFinalResult.SUCCESS)
        assertThat(attempt.refundNotificationReceipts).hasSize(1)

        assertThatIllegalArgumentException().isThrownBy {
            refund.adjudicateManualResult(
                attempt.id,
                "manual-resolution-success-retry",
                "reference-refund-reviewer",
                "REFUND_REVIEW_OPERATOR",
                "CONFIRM_SUCCESS",
                "重复处置",
                "evidence://refund/success",
                LocalDateTime.parse("2026-09-22T08:01:00"),
            )
        }
        assertThat(attempt.refundNotificationReceipts).hasSize(1)
    }

    @Test
    @DisplayName("PAY-AC-024/025/086/099 — UNKNOWN 退款人工失败只释放一次预算")
    fun `manual failure adjudication releases an active reservation once`() {
        val (refund, attempt) = unknownRefund()

        val outcome = refund.adjudicateManualResult(
            attemptId = attempt.id,
            resolutionIdentity = "manual-resolution-failure",
            operatorIdentity = "reference-refund-reviewer",
            operatorRole = "REFUND_REVIEW_OPERATOR",
            outcome = "CONFIRM_FAILURE",
            reason = "渠道确认未发生退款",
            evidence = "evidence://refund/failure",
            adjudicatedAt = LocalDateTime.parse("2026-09-22T08:00:00"),
        )

        assertThat(outcome.reservationReleasedNow).isTrue()
        assertThat(outcome.reservationConvertedToSuccessNow).isFalse()
        assertThat(refund.status).isEqualTo(RefundStatus.FAILED)
        assertThat(refund.reservationActive).isFalse()
        assertThat(refund.reservationReleased).isTrue()
        assertThat(refund.reservationConvertedToSuccess).isFalse()
        assertThat(attempt.finalResult).isEqualTo(RefundAttemptFinalResult.FAILED)
        assertThat(attempt.refundNotificationReceipts).hasSize(1)

        assertThatIllegalArgumentException().isThrownBy {
            refund.adjudicateManualResult(
                attempt.id,
                "manual-resolution-failure-retry",
                "reference-refund-reviewer",
                "REFUND_REVIEW_OPERATOR",
                "CONFIRM_FAILURE",
                "重复处置",
                "evidence://refund/failure",
                LocalDateTime.parse("2026-09-22T08:01:00"),
            )
        }
        assertThat(attempt.refundNotificationReceipts).hasSize(1)
    }

    private fun unknownRefund(): Pair<Refund, RefundAttempt> {
        val now = LocalDateTime.parse("2026-09-22T07:00:00")
        val refund = Refund(
            paymentId = PaymentId.parse("018f22a0-0000-7000-8000-000000000001"),
            merchantId = "M-001",
            merchantRefundNumber = "R-001",
            idempotencyKey = "refund-key-001",
            amount = BigDecimal("40.00"),
            currency = "CNY",
            reason = "reference refund",
            paymentMethod = "REFERENCE",
            status = RefundStatus.RESULT_UNKNOWN,
            requestedAt = now.minusMinutes(10),
            refundDeadlineAt = now.plusDays(30),
        )
        refund.id = RefundId.parse("018f22a0-0000-7000-8000-000000000002")
        val attempt = RefundAttempt(
            channelId = "C-001",
            channelConfigurationId = "018f22a0-0000-7000-8000-000000000003",
            channelConfigurationSnapshot = "channelId=C-001;currency=CNY",
            requestIdentity = "refund-request-001",
            status = RefundAttemptStatus.RESULT_UNKNOWN,
            initiatedAt = now.minusMinutes(5),
            reviewAfterAt = now,
        )
        attempt.id = RefundAttemptId.parse("018f22a0-0000-7000-8000-000000000004")
        refund.attempts.add(attempt)
        return refund to attempt
    }
}
