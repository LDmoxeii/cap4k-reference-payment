package com.only4.cap4k.reference.payment.adapter.contract

import com.only4.cap4k.reference.payment.contract.common.ChannelResultDisposition as PublicChannelResultDisposition
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition as PaymentResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus

/**
 * The transactional model keeps its more detailed enums. This mapper is the single boundary that
 * projects those implementation details into the unified reference contract vocabulary.
 */
object ReferenceContractStatusMapper {
    fun paymentFinality(
        status: PaymentStatus,
        settlementBlocked: Boolean = false,
        blockingReviewCount: Int = 0,
    ): Finality = when {
        settlementBlocked || blockingReviewCount > 0 -> Finality.REVIEW_REQUIRED
        status in setOf(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CLOSED) -> Finality.FINAL
        else -> Finality.NON_FINAL
    }

    fun paymentFinality(
        internalName: String,
        settlementBlocked: Boolean = false,
        blockingReviewCount: Int = 0,
    ): Finality = paymentFinality(PaymentStatus.valueOf(internalName), settlementBlocked, blockingReviewCount)

    fun paymentDisposition(disposition: PaymentResultDisposition): PublicChannelResultDisposition = when (disposition) {
        PaymentResultDisposition.SUCCESS_ACCEPTED,
        PaymentResultDisposition.FAILURE_ACCEPTED,
        PaymentResultDisposition.UNKNOWN_ACCEPTED,
        -> PublicChannelResultDisposition.ACCEPTED

        PaymentResultDisposition.ACCEPTED_DUPLICATE,
        PaymentResultDisposition.REJECTED_DUPLICATE,
        -> PublicChannelResultDisposition.DUPLICATE

        PaymentResultDisposition.REJECTED -> PublicChannelResultDisposition.REJECTED_INVALID
        PaymentResultDisposition.ATTEMPT_NOT_FOUND -> PublicChannelResultDisposition.UNKNOWN_REFERENCE
        PaymentResultDisposition.LATE -> PublicChannelResultDisposition.LATE
        PaymentResultDisposition.CONFLICT -> PublicChannelResultDisposition.CONFLICTING
        PaymentResultDisposition.RECEIVED -> error("non-terminal payment result disposition cannot cross the contract boundary")
    }

    fun paymentDisposition(internalName: String): PublicChannelResultDisposition =
        paymentDisposition(PaymentResultDisposition.valueOf(internalName))

    fun refundDisposition(disposition: RefundResultDisposition): PublicChannelResultDisposition = when (disposition) {
        RefundResultDisposition.SUCCESS_ACCEPTED,
        RefundResultDisposition.FAILURE_ACCEPTED,
        RefundResultDisposition.RETRYABLE_FAILURE_ACCEPTED,
        RefundResultDisposition.UNKNOWN_ACCEPTED,
        -> PublicChannelResultDisposition.ACCEPTED

        RefundResultDisposition.ACCEPTED_DUPLICATE,
        RefundResultDisposition.REJECTED_DUPLICATE,
        -> PublicChannelResultDisposition.DUPLICATE

        RefundResultDisposition.REJECTED -> PublicChannelResultDisposition.REJECTED_INVALID
        RefundResultDisposition.ATTEMPT_NOT_FOUND -> PublicChannelResultDisposition.UNKNOWN_REFERENCE
        RefundResultDisposition.CONFLICT -> PublicChannelResultDisposition.CONFLICTING
        RefundResultDisposition.RECEIVED -> error("non-terminal refund result disposition cannot cross the contract boundary")
    }

    fun refundDisposition(internalName: String): PublicChannelResultDisposition =
        refundDisposition(RefundResultDisposition.valueOf(internalName))

    fun refundStatus(status: RefundStatus): String = when (status) {
        RefundStatus.REVIEW_REQUIRED -> "RESULT_UNKNOWN"
        else -> status.name
    }

    fun refundStatus(internalName: String): String = refundStatus(RefundStatus.valueOf(internalName))

    fun refundAttemptStatus(status: RefundAttemptStatus): String = when (status) {
        RefundAttemptStatus.REVIEW_REQUIRED -> "RESULT_UNKNOWN"
        else -> status.name
    }

    fun refundAttemptStatus(internalName: String): String =
        refundAttemptStatus(RefundAttemptStatus.valueOf(internalName))

    fun refundFinality(status: RefundStatus, settlementBlocked: Boolean = false): Finality = when {
        status == RefundStatus.REVIEW_REQUIRED || settlementBlocked -> Finality.REVIEW_REQUIRED
        status in setOf(RefundStatus.SUCCEEDED, RefundStatus.FAILED) -> Finality.FINAL
        else -> Finality.NON_FINAL
    }

    fun refundFinality(internalName: String, settlementBlocked: Boolean = false): Finality =
        refundFinality(RefundStatus.valueOf(internalName), settlementBlocked)

    /** One public status can represent more than one internal persistence status. */
    fun refundStatusesForFilter(publicStatus: String): Set<RefundStatus> = when (publicStatus.trim().uppercase()) {
        "RESULT_UNKNOWN" -> setOf(RefundStatus.RESULT_UNKNOWN, RefundStatus.REVIEW_REQUIRED)
        "REQUESTED" -> setOf(RefundStatus.REQUESTED)
        "PROCESSING" -> setOf(RefundStatus.PROCESSING)
        "SUCCEEDED" -> setOf(RefundStatus.SUCCEEDED)
        "FAILED" -> setOf(RefundStatus.FAILED)
        // The unified contract reserves REJECTED even though this backend currently rejects
        // synchronous invalid requests before a Refund aggregate exists.
        "REJECTED" -> emptySet()
        else -> throw IllegalArgumentException("不支持的 Refund status")
    }
}
