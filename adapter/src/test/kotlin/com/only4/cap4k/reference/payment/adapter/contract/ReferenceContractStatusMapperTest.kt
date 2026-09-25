package com.only4.cap4k.reference.payment.adapter.contract

import com.only4.cap4k.reference.payment.contract.common.ChannelResultDisposition
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition as PaymentDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReferenceContractStatusMapperTest {
    @Test
    fun `payment result dispositions use only the six public values`() {
        val expected = mapOf(
            PaymentDisposition.SUCCESS_ACCEPTED to ChannelResultDisposition.ACCEPTED,
            PaymentDisposition.FAILURE_ACCEPTED to ChannelResultDisposition.ACCEPTED,
            PaymentDisposition.UNKNOWN_ACCEPTED to ChannelResultDisposition.ACCEPTED,
            PaymentDisposition.ACCEPTED_DUPLICATE to ChannelResultDisposition.DUPLICATE,
            PaymentDisposition.REJECTED_DUPLICATE to ChannelResultDisposition.DUPLICATE,
            PaymentDisposition.REJECTED to ChannelResultDisposition.REJECTED_INVALID,
            PaymentDisposition.ATTEMPT_NOT_FOUND to ChannelResultDisposition.UNKNOWN_REFERENCE,
            PaymentDisposition.LATE to ChannelResultDisposition.LATE,
            PaymentDisposition.CONFLICT to ChannelResultDisposition.CONFLICTING,
        )

        assertEquals(expected, expected.keys.associateWith(ReferenceContractStatusMapper::paymentDisposition))
        assertFailsWith<IllegalStateException> {
            ReferenceContractStatusMapper.paymentDisposition(PaymentDisposition.RECEIVED)
        }
    }

    @Test
    fun `refund result dispositions share the payment public vocabulary`() {
        val expected = mapOf(
            RefundResultDisposition.SUCCESS_ACCEPTED to ChannelResultDisposition.ACCEPTED,
            RefundResultDisposition.FAILURE_ACCEPTED to ChannelResultDisposition.ACCEPTED,
            RefundResultDisposition.RETRYABLE_FAILURE_ACCEPTED to ChannelResultDisposition.ACCEPTED,
            RefundResultDisposition.UNKNOWN_ACCEPTED to ChannelResultDisposition.ACCEPTED,
            RefundResultDisposition.ACCEPTED_DUPLICATE to ChannelResultDisposition.DUPLICATE,
            RefundResultDisposition.REJECTED_DUPLICATE to ChannelResultDisposition.DUPLICATE,
            RefundResultDisposition.REJECTED to ChannelResultDisposition.REJECTED_INVALID,
            RefundResultDisposition.ATTEMPT_NOT_FOUND to ChannelResultDisposition.UNKNOWN_REFERENCE,
            RefundResultDisposition.CONFLICT to ChannelResultDisposition.CONFLICTING,
        )

        assertEquals(expected, expected.keys.associateWith(ReferenceContractStatusMapper::refundDisposition))
        assertFailsWith<IllegalStateException> {
            ReferenceContractStatusMapper.refundDisposition(RefundResultDisposition.RECEIVED)
        }
    }

    @Test
    fun `refund review state is externally result unknown with review finality`() {
        assertEquals("RESULT_UNKNOWN", ReferenceContractStatusMapper.refundStatus(RefundStatus.REVIEW_REQUIRED))
        assertEquals(
            Finality.REVIEW_REQUIRED,
            ReferenceContractStatusMapper.refundFinality(RefundStatus.REVIEW_REQUIRED),
        )
        assertEquals(
            "RESULT_UNKNOWN",
            ReferenceContractStatusMapper.refundAttemptStatus(RefundAttemptStatus.REVIEW_REQUIRED),
        )
        assertEquals("RESULT_UNKNOWN", ReferenceContractStatusMapper.refundStatus(RefundStatus.RESULT_UNKNOWN))
        assertEquals(Finality.NON_FINAL, ReferenceContractStatusMapper.refundFinality(RefundStatus.RESULT_UNKNOWN))
        assertEquals(
            Finality.REVIEW_REQUIRED,
            ReferenceContractStatusMapper.refundFinality(RefundStatus.SUCCEEDED, settlementBlocked = true),
        )
    }

    @Test
    fun `result unknown list filter includes internal review state`() {
        assertEquals(
            setOf(RefundStatus.RESULT_UNKNOWN, RefundStatus.REVIEW_REQUIRED),
            ReferenceContractStatusMapper.refundStatusesForFilter("result_unknown"),
        )
        assertEquals(emptySet(), ReferenceContractStatusMapper.refundStatusesForFilter("REJECTED"))
        assertFailsWith<IllegalArgumentException> {
            ReferenceContractStatusMapper.refundStatusesForFilter("REVIEW_REQUIRED")
        }
    }
}
