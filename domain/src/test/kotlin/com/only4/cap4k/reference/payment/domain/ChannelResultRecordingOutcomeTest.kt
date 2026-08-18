package com.only4.cap4k.reference.payment.domain

import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class ChannelResultRecordingOutcomeTest {
    @Test
    fun `accepted success exposes derived domain semantics`() {
        val outcome = successfulOutcome()

        assertThat(outcome.accepted).isTrue()
        assertThat(outcome.duplicate).isFalse()
        assertThat(outcome.rejected).isFalse()
        assertThat(outcome.conflicting).isFalse()
        assertThat(outcome.successFactFormedNow).isTrue()
    }

    @Test
    fun `attempt not found is a rejected outcome without a fabricated attempt status`() {
        val outcome = ChannelResultRecordingOutcome(
            paymentStatus = PaymentStatus.PROCESSING,
            attemptStatus = null,
            notificationReceiveCount = 1,
            disposition = ChannelResultDisposition.ATTEMPT_NOT_FOUND,
            rejectionSummary = "attempt does not belong to payment",
            conflictSummary = null,
            successFactFormedNow = false,
        )

        assertThat(outcome.rejected).isTrue()
        assertThat(outcome.attemptStatus).isNull()
    }

    @Test
    fun `invalid outcome combinations are rejected`() {
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(notificationReceiveCount = 0)
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(disposition = ChannelResultDisposition.RECEIVED, successFactFormedNow = false)
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(attemptStatus = null)
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(
                disposition = ChannelResultDisposition.ATTEMPT_NOT_FOUND,
                rejectionSummary = "missing",
                successFactFormedNow = false,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(
                disposition = ChannelResultDisposition.REJECTED,
                rejectionSummary = null,
                successFactFormedNow = false,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(rejectionSummary = "unexpected")
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(
                disposition = ChannelResultDisposition.CONFLICT,
                successFactFormedNow = false,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(conflictSummary = "unexpected")
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(
                disposition = ChannelResultDisposition.FAILURE_ACCEPTED,
                successFactFormedNow = true,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(
                disposition = ChannelResultDisposition.ACCEPTED_DUPLICATE,
                successFactFormedNow = true,
            )
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(paymentStatus = PaymentStatus.PROCESSING)
        }
        assertThatIllegalArgumentException().isThrownBy {
            successfulOutcome().copy(
                disposition = ChannelResultDisposition.FAILURE_ACCEPTED,
                attemptStatus = PaymentAttemptStatus.PROCESSING,
                successFactFormedNow = false,
            )
        }
    }

    private fun successfulOutcome(): ChannelResultRecordingOutcome = ChannelResultRecordingOutcome(
        paymentStatus = PaymentStatus.SUCCEEDED,
        attemptStatus = PaymentAttemptStatus.SUCCEEDED,
        notificationReceiveCount = 1,
        disposition = ChannelResultDisposition.SUCCESS_ACCEPTED,
        rejectionSummary = null,
        conflictSummary = null,
        successFactFormedNow = true,
    )
}
