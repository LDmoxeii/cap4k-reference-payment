package com.only4.cap4k.reference.payment.domain.aggregates.refund

import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.values.RefundResultRecordingOutcome
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class RefundResultRecordingOutcomeTest {
    @Test
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
}
