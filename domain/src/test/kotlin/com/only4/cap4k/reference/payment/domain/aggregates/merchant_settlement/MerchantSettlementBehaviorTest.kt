package com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement

import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisorSupport
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.events.MerchantSettlementCompletedDomainEvent
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementLineSourceKind
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.factory.MerchantSettlementFactory
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MerchantSettlementBehaviorTest {
    private lateinit var domainEvents: RecordingDomainEventSupervisor

    @BeforeEach
    fun configureDomainEvents() {
        domainEvents = RecordingDomainEventSupervisor()
        DomainEventSupervisorSupport.configure(domainEvents)
    }

    @AfterEach
    fun releaseDomainEvents() {
        DomainEventSupervisorSupport.release(domainEvents)
    }
    @Test
    fun `confirmation freezes positive composition while zero completes without transfer and negative remains review-only`() {
        val positive = settlement("127.00")
        assertThat(positive.confirmComposition(OPERATOR, ROLE, NOW)).isEqualTo(MerchantSettlementStatus.CONFIRMED)
        assertThat(positive.compositionFrozen).isTrue()
        assertThat(positive.confirmedBy).isEqualTo(OPERATOR)
        assertThat(positive.settledFactFormed).isFalse()

        val zero = settlement("0.00")
        assertThat(zero.confirmComposition(OPERATOR, ROLE, NOW)).isEqualTo(MerchantSettlementStatus.SUCCEEDED)
        assertThat(zero.settledFactFormed).isTrue()
        assertThat(zero.completedAt).isEqualTo(NOW)
        assertThat(zero.settlementExecutionAttempts).isEmpty()
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)
        assertThatThrownBy { zero.startAttempt() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("no positive amount")

        val negative = settlement("-30.00")
        assertThat(negative.confirmComposition(OPERATOR, ROLE, NOW)).isEqualTo(MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED)
        assertThat(negative.settlementLines).hasSize(1)
        assertThatThrownBy { negative.startAttempt() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("no positive amount")
    }

    @Test
    fun `first verified success forms one settled fact and exact replay only increments receipt counters`() {
        val settlement = confirmedSettlement()
        val attempt = settlement.startAcceptedAttempt()

        val first = settlement.recordResult(attempt, result = "SUCCESS", notificationId = "N-1", fingerprint = "fp-1")
        val replay = settlement.recordResult(attempt, result = "SUCCESS", notificationId = "N-1", fingerprint = "fp-1", receivedAt = NOW.plusMinutes(3))

        assertThat(first.disposition).isEqualTo(SettlementResultDisposition.SUCCESS_ACCEPTED)
        assertThat(first.settledFactFormedNow).isTrue()
        assertThat(replay.disposition).isEqualTo(SettlementResultDisposition.ACCEPTED_DUPLICATE)
        assertThat(replay.settledFactFormedNow).isFalse()
        assertThat(settlement.status).isEqualTo(MerchantSettlementStatus.SUCCEEDED)
        assertThat(settlement.settledFactFormed).isTrue()
        assertThat(attempt.notificationReceiveCount).isEqualTo(2)
        assertThat(attempt.settlementResultReceipts).hasSize(1)
        assertThat(attempt.settlementResultReceipts.single().receiveCount).isEqualTo(2)
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)
    }

    @Test
    fun `same notification with another payload and late opposite final result are conflicts without success rollback`() {
        val settlement = confirmedSettlement()
        val attempt = settlement.startAcceptedAttempt()
        settlement.recordResult(attempt, result = "SUCCESS", notificationId = "N-1", fingerprint = "fp-success")

        val identityConflict = settlement.recordResult(
            attempt,
            result = "SUCCESS",
            notificationId = "N-1",
            fingerprint = "fp-mutated",
            receivedAt = NOW.plusMinutes(3),
        )
        val lateFailure = settlement.recordResult(
            attempt,
            result = "FAILED",
            notificationId = "N-2",
            fingerprint = "fp-failure",
            receivedAt = NOW.plusMinutes(4),
        )

        assertThat(identityConflict.disposition).isEqualTo(SettlementResultDisposition.CONFLICT)
        assertThat(lateFailure.disposition).isEqualTo(SettlementResultDisposition.CONFLICT)
        assertThat(settlement.status).isEqualTo(MerchantSettlementStatus.SUCCEEDED)
        assertThat(attempt.status).isEqualTo(SettlementExecutionAttemptStatus.CONFLICT_REVIEW_REQUIRED)
        assertThat(attempt.finalResult).isEqualTo(SettlementExecutionFinalResult.SUCCESS)
        assertThat(attempt.settlementResultReceipts).hasSize(2)
        assertThat(attempt.conflictingNotificationCount).isEqualTo(2)
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)
    }

    @Test
    fun `unknown result blocks retry until frozen threshold and authorized adjudication appends final evidence`() {
        val settlement = confirmedSettlement()
        val attempt = settlement.startAcceptedAttempt()
        val unknown = settlement.recordResult(attempt, result = "UNKNOWN", notificationId = "N-U", fingerprint = "fp-u")

        assertThat(unknown.disposition).isEqualTo(SettlementResultDisposition.UNKNOWN_ACCEPTED)
        assertThat(settlement.status).isEqualTo(MerchantSettlementStatus.RESULT_UNKNOWN)
        assertThat(attempt.finalResult).isEqualTo(SettlementExecutionFinalResult.UNKNOWN)
        assertThatThrownBy { settlement.startAttempt(requestIdentity = "REQ-2") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("result is unknown")
        assertThat(settlement.markUnknownReviewRequired(attempt.reviewAfterAt.minusSeconds(1))).isFalse()
        assertThat(settlement.markUnknownReviewRequired(attempt.reviewAfterAt)).isTrue()
        assertThat(attempt.status).isEqualTo(SettlementExecutionAttemptStatus.REVIEW_REQUIRED)

        val adjudicated = settlement.adjudicateUnknownResult(
            attemptId = attempt.id,
            operatorIdentity = OPERATOR,
            operatorRole = ROLE,
            finalResult = "SUCCESS",
            adjudicatedAt = attempt.reviewAfterAt.plusMinutes(1),
            evidence = "bank trace verified by finance",
        )

        assertThat(adjudicated.disposition).isEqualTo(SettlementResultDisposition.SUCCESS_ACCEPTED)
        assertThat(adjudicated.settledFactFormedNow).isTrue()
        assertThat(settlement.status).isEqualTo(MerchantSettlementStatus.SUCCEEDED)
        assertThat(attempt.finalResult).isEqualTo(SettlementExecutionFinalResult.SUCCESS)
        assertThat(attempt.settlementResultReceipts).hasSize(2)
        assertThat(attempt.settlementResultReceipts.last().resultCode).isEqualTo("MANUAL_ADJUDICATION")
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)
        assertThatThrownBy {
            settlement.adjudicateUnknownResult(
                attempt.id, OPERATOR, ROLE, "FAILED", attempt.reviewAfterAt.plusMinutes(2), "duplicate review"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `unconfirmed settlement can return for adjustment and link a fresh predecessor chain`() {
        val previous = settlement("127.00")
        val replacement = settlement("127.00").also {
            it.id = MerchantSettlementId.parse("018f22a0-0000-7000-8000-000000000002")
        }

        previous.returnForAdjustment(OPERATOR, ROLE, "refresh candidate evidence", NOW)
        replacement.linkPredecessor(previous.id)
        previous.linkReplacement(replacement.id)

        assertThat(previous.status).isEqualTo(MerchantSettlementStatus.VOIDED)
        assertThat(previous.voidReason).isEqualTo("RETURN_FOR_ADJUSTMENT: refresh candidate evidence")
        assertThat(previous.effectiveScopeIdentity).isNull()
        assertThat(previous.settlementLines.single().effectiveConsumptionIdentity).isNull()
        assertThat(previous.replacementSettlementId).isEqualTo(replacement.id.toString())
        assertThat(replacement.predecessorSettlementId).isEqualTo(previous.id.toString())
        assertThat(replacement.effectiveScopeIdentity).isEqualTo(previous.scopeIdentity)

        val confirmed = confirmedSettlement()
        assertThatThrownBy {
            confirmed.returnForAdjustment(OPERATOR, ROLE, "too late", NOW.plusMinutes(1))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cannot be returned")
    }

    @Test
    fun `replacement activation claims canonical effective ownership idempotently`() {
        val replacement = settlement("127.00")
        replacement.effectiveScopeIdentity = null
        replacement.settlementLines.forEach { it.effectiveConsumptionIdentity = null }

        replacement.activateEffectiveOwnership()
        val firstConsumptionIdentity = replacement.settlementLines.single().effectiveConsumptionIdentity

        assertThat(replacement.effectiveScopeIdentity).isEqualTo(replacement.scopeIdentity)
        assertThat(firstConsumptionIdentity).isNotBlank()

        replacement.activateEffectiveOwnership()

        assertThat(replacement.effectiveScopeIdentity).isEqualTo(replacement.scopeIdentity)
        assertThat(replacement.settlementLines.single().effectiveConsumptionIdentity)
            .isEqualTo(firstConsumptionIdentity)

        replacement.voidBeforeExecution(OPERATOR, ROLE, "replacement cancelled", NOW.plusMinutes(1))
        assertThatThrownBy { replacement.activateEffectiveOwnership() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cannot activate effective ownership")
    }

    @Test
    fun `only authorized operators may confirm adjudicate or void`() {
        val prepared = settlement("10.00")
        assertThatThrownBy { prepared.confirmComposition(OPERATOR, "VIEWER", NOW) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not authorized")

        prepared.voidBeforeExecution(OPERATOR, ROLE, "merchant correction", NOW)
        assertThat(prepared.status).isEqualTo(MerchantSettlementStatus.VOIDED)
        assertThat(prepared.effectiveScopeIdentity).isNull()
        assertThat(prepared.settlementLines.single().effectiveConsumptionIdentity).isNull()
        val replacementId = MerchantSettlementId.parse("018f22a0-0000-7000-8000-000000000099")
        prepared.linkReplacement(replacementId)
        assertThat(prepared.replacementSettlementId).isEqualTo(replacementId.toString())

        val confirmed = confirmedSettlement()
        assertThatThrownBy { confirmed.voidBeforeExecution(OPERATOR, ROLE, "too late", NOW.plusMinutes(1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cannot be voided")
    }

    private fun confirmedSettlement(): MerchantSettlement = settlement("127.00").also {
        it.confirmComposition(OPERATOR, ROLE, NOW)
    }

    private fun MerchantSettlement.startAttempt(requestIdentity: String = "REQ-1"): SettlementExecutionAttempt =
        startExecutionAttempt(
            operatorIdentity = OPERATOR,
            operatorRole = ROLE,
            requestedAt = NOW.plusMinutes(1),
            reviewAfterMinutes = 30,
            executionGroupIdentity = "GROUP-1",
            requestIdentity = requestIdentity,
        ).also { attempt ->
            attempt.id = SettlementExecutionAttemptId.parse("018f22a0-0000-7000-8000-000000000010")
        }

    private fun MerchantSettlement.startAcceptedAttempt(): SettlementExecutionAttempt = startAttempt().also { attempt ->
        markExecutionAccepted(attempt.id, "EXT-1", NOW.plusMinutes(2))
    }

    private fun MerchantSettlement.recordResult(
        attempt: SettlementExecutionAttempt,
        result: String,
        notificationId: String,
        fingerprint: String,
        receivedAt: LocalDateTime = NOW.plusMinutes(2),
    ) = recordSettlementResult(
        attemptId = attempt.id,
        notificationIdentity = notificationId,
        payloadFingerprint = fingerprint,
        channelId = "C-001",
        executionGroupIdentity = "GROUP-1",
        requestIdentity = attempt.requestIdentity,
        externalSettlementIdentity = "EXT-1",
        amount = attempt.amount,
        currency = attempt.currency,
        result = result,
        resultCode = "00",
        occurredAt = receivedAt.minusSeconds(1),
        receivedAt = receivedAt,
        verified = true,
        verificationSummary = "verified",
    )

    private fun settlement(net: String): MerchantSettlement {
        val netAmount = BigDecimal(net)
        val transactionKind = if (netAmount.signum() < 0) ReconciliationTransactionKind.REFUND else ReconciliationTransactionKind.PAYMENT
        val grossAmount = netAmount.abs()
        val line = SettlementLineCreation(
            lineIdentity = "LINE-1",
            sourceKind = if (transactionKind == ReconciliationTransactionKind.REFUND) SettlementLineSourceKind.REFUND else SettlementLineSourceKind.PAYMENT,
            transactionKind = transactionKind,
            sourceFactIdentity = "FACT-1",
            effectiveConsumptionIdentity = "ACTIVE-FACT-1",
            feeFactIdentity = null,
            paymentId = null,
            paymentAttemptId = null,
            refundId = null,
            refundAttemptId = null,
            reconciliationBatchId = null,
            reconciliationRunId = null,
            reconciliationItemId = null,
            reconciliationConfirmationFactId = null,
            externalTransactionIdentity = "TX-1",
            grossAmount = grossAmount,
            feeAmount = BigDecimal.ZERO,
            signedNetAmount = netAmount,
            currency = "CNY",
            occurredAt = NOW.minusHours(1),
            recordedAt = NOW,
            feeBasisPoints = null,
            feeFixedAmount = null,
            feeRoundingMode = null,
            feeCurrencyPrecision = null,
            feeCalculationAmount = null,
            eligibilityBasis = "verified fact",
            confirmationReason = null,
            confirmationEvidence = null,
            adjustmentSourceIdentity = null,
            adjustmentEvidence = null,
        )
        return MerchantSettlementFactory().create(
            MerchantSettlementFactory.Payload(
                merchantId = "M-001",
                channelId = "C-001",
                currency = "CNY",
                periodStart = NOW.toLocalDate().atStartOfDay(),
                periodEnd = NOW.toLocalDate().plusDays(1).atStartOfDay(),
                businessTimezone = "Asia/Shanghai",
                scopeIdentity = "SCOPE-1",
                effectiveScopeIdentity = "SCOPE-1",
                status = MerchantSettlementStatus.PREPARED,
                eligibleCount = 1,
                excludedCount = 0,
                blockerSummary = null,
                paymentGrossAmount = if (transactionKind == ReconciliationTransactionKind.PAYMENT) grossAmount else BigDecimal.ZERO,
                refundGrossAmount = if (transactionKind == ReconciliationTransactionKind.REFUND) grossAmount else BigDecimal.ZERO,
                feeTotalAmount = BigDecimal.ZERO,
                adjustmentTotalAmount = BigDecimal.ZERO,
                netAmount = netAmount,
                executionGroupIdentity = null,
                predecessorSettlementId = null,
                replacementSettlementId = null,
                confirmedBy = null,
                confirmedAt = null,
                voidedBy = null,
                voidReason = null,
                voidedAt = null,
                externalSettlementIdentity = null,
                completedAt = null,
                lastRejectionSummary = null,
                lastConflictSummary = null,
                lastReviewSummary = null,
                settlementLines = listOf(line),
            )
        ).also {
            it.id = MerchantSettlementId.parse("018f22a0-0000-7000-8000-000000000001")
            it.onCreate()
        }
    }

    private class RecordingDomainEventSupervisor : DomainEventSupervisor {
        val attached = mutableListOf<Any>()

        override fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
            domainEventPayload: DOMAIN_EVENT,
            entity: ENTITY,
            schedule: LocalDateTime,
        ) {
            attached += domainEventPayload
        }

        override fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
            entity: ENTITY,
            schedule: LocalDateTime,
            domainEventPayloadSupplier: () -> DOMAIN_EVENT,
        ) {
            attached += domainEventPayloadSupplier()
        }

        override fun <DOMAIN_EVENT : Any, ENTITY : Any> detach(
            domainEventPayload: DOMAIN_EVENT,
            entity: ENTITY,
        ) {
            attached.remove(domainEventPayload)
        }
    }

    companion object {
        private const val OPERATOR = "finance-1"
        private const val ROLE = "SETTLEMENT_OPERATOR"
        private val NOW = LocalDateTime.parse("2026-08-20T09:00:00")
    }
}
