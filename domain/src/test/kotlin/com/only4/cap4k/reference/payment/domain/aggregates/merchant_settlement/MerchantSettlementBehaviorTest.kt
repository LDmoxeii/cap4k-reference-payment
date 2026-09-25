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
import org.junit.jupiter.api.DisplayName
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
    @DisplayName("PAY-AC-060/066/067 — 结算构成冻结、零/负净额边界")
    fun `confirmation freezes positive composition while zero completes without transfer and negative remains review-only`() {
        // Given：同一结算周期分别准备正、零、负三种净额，观察候选资格与划拨副作用。
        val positive = settlement("127.00")
        // When：确认正净额、确认零净额、确认负净额；只有正净额允许后续执行划拨。
        assertThat(positive.confirmComposition(OPERATOR, ROLE, NOW, "reviewed", "ledger-1")).isEqualTo(MerchantSettlementStatus.CONFIRMED)
        assertThat(positive.compositionFrozen).isTrue()
        assertThat(positive.confirmedBy).isEqualTo(OPERATOR)
        assertThat(positive.settledFactFormed).isFalse()

        val zero = settlement("0.00")
        assertThat(zero.confirmComposition(OPERATOR, ROLE, NOW, "reviewed", "ledger-1")).isEqualTo(MerchantSettlementStatus.SUCCEEDED)
        assertThat(zero.settledFactFormed).isTrue()
        assertThat(zero.completedAt).isEqualTo(NOW)
        assertThat(zero.settlementExecutionAttempts).isEmpty()
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)
        assertThatThrownBy { zero.startAttempt() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("没有可划拨的正净额")

        // Then：结算状态、compositionFrozen、settled fact、execution attempt 与 Domain Event 数量共同构成证据。
        val negative = settlement("-30.00")
        assertThat(negative.confirmComposition(OPERATOR, ROLE, NOW, "reviewed", "ledger-1")).isEqualTo(MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED)
        assertThat(negative.settlementLines).hasSize(1)
        assertThatThrownBy { negative.startAttempt() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("没有可划拨的正净额")
    }

    @Test
    @DisplayName("PAY-AC-063 — 结算成功事实与重复回放幂等")
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
    @DisplayName("PAY-AC-065/088 — 结算冲突证据与成功不可回退")
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
    @DisplayName("PAY-AC-064/086 — 未知结果复核与授权裁决")
    fun `unknown result blocks retry until frozen threshold and authorized adjudication appends final evidence`() {
        val settlement = confirmedSettlement()
        val attempt = settlement.startAcceptedAttempt()
        val unknown = settlement.recordResult(attempt, result = "UNKNOWN", notificationId = "N-U", fingerprint = "fp-u")

        assertThat(unknown.disposition).isEqualTo(SettlementResultDisposition.UNKNOWN_ACCEPTED)
        assertThat(settlement.status).isEqualTo(MerchantSettlementStatus.RESULT_UNKNOWN)
        assertThat(attempt.finalResult).isEqualTo(SettlementExecutionFinalResult.UNKNOWN)
        assertThatThrownBy { settlement.startAttempt(requestIdentity = "REQ-2") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("执行结果仍未知")
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
    @DisplayName("PAY-AC-097 — UNKNOWN 在原执行身份上收敛为渠道最终结果")
    fun `trusted final callback converges unknown under the original attempt identity`() {
        val successSettlement = confirmedSettlement()
        val successAttempt = successSettlement.startAcceptedAttempt()

        val unknown = successSettlement.recordResult(
            successAttempt,
            result = "UNKNOWN",
            notificationId = "N-U",
            fingerprint = "fp-u",
        )
        val unknownReplay = successSettlement.recordResult(
            successAttempt,
            result = "UNKNOWN",
            notificationId = "N-U",
            fingerprint = "fp-u",
            receivedAt = NOW.plusMinutes(3),
        )
        val convergedSuccess = successSettlement.recordResult(
            successAttempt,
            result = "SUCCESS",
            notificationId = "N-S",
            fingerprint = "fp-s",
            receivedAt = NOW.plusMinutes(4),
        )

        assertThat(unknown.disposition).isEqualTo(SettlementResultDisposition.UNKNOWN_ACCEPTED)
        assertThat(unknownReplay.disposition).isEqualTo(SettlementResultDisposition.ACCEPTED_DUPLICATE)
        assertThat(convergedSuccess.disposition).isEqualTo(SettlementResultDisposition.SUCCESS_ACCEPTED)
        assertThat(convergedSuccess.settledFactFormedNow).isTrue()
        assertThat(successSettlement.status).isEqualTo(MerchantSettlementStatus.SUCCEEDED)
        assertThat(successSettlement.settlementExecutionAttempts).containsExactly(successAttempt)
        assertThat(successAttempt.status).isEqualTo(SettlementExecutionAttemptStatus.SUCCEEDED)
        assertThat(successAttempt.finalResult).isEqualTo(SettlementExecutionFinalResult.SUCCESS)
        assertThat(successAttempt.settlementResultReceipts).hasSize(2)
        assertThat(successAttempt.settlementResultReceipts.first().receiveCount).isEqualTo(2)
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)

        val lateFailure = successSettlement.recordResult(
            successAttempt,
            result = "FAILED",
            notificationId = "N-LATE-F",
            fingerprint = "fp-late-f",
            receivedAt = NOW.plusMinutes(5),
        )
        assertThat(lateFailure.disposition).isEqualTo(SettlementResultDisposition.CONFLICT)
        assertThat(successSettlement.status).isEqualTo(MerchantSettlementStatus.SUCCEEDED)
        assertThat(successAttempt.finalResult).isEqualTo(SettlementExecutionFinalResult.SUCCESS)
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)

        val failedSettlement = confirmedSettlement().also {
            it.id = MerchantSettlementId.parse("018f22a0-0000-7000-8000-000000000003")
        }
        val failedAttempt = failedSettlement.startAcceptedAttempt()
        failedSettlement.recordResult(
            failedAttempt,
            result = "UNKNOWN",
            notificationId = "N-U-F",
            fingerprint = "fp-u-f",
        )
        val convergedFailure = failedSettlement.recordResult(
            failedAttempt,
            result = "FAILED",
            notificationId = "N-F",
            fingerprint = "fp-f",
            receivedAt = NOW.plusMinutes(4),
        )

        assertThat(convergedFailure.disposition).isEqualTo(SettlementResultDisposition.FAILURE_ACCEPTED)
        assertThat(failedSettlement.status).isEqualTo(MerchantSettlementStatus.FAILED)
        assertThat(failedSettlement.settlementExecutionAttempts).containsExactly(failedAttempt)
        assertThat(failedAttempt.status).isEqualTo(SettlementExecutionAttemptStatus.FAILED)
        assertThat(failedAttempt.finalResult).isEqualTo(SettlementExecutionFinalResult.FAILED)
        assertThat(failedAttempt.settlementResultReceipts).hasSize(2)
        assertThat(domainEvents.attached.filterIsInstance<MerchantSettlementCompletedDomainEvent>()).hasSize(1)
    }

    @Test
    @DisplayName("PAY-AC-067/068 — 未确认结算的追加式调整链")
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
        assertThat(previous.replacementSettlementId).isEqualTo(replacement.id)
        assertThat(replacement.predecessorSettlementId).isEqualTo(previous.id)
        assertThat(replacement.effectiveScopeIdentity).isEqualTo(previous.scopeIdentity)

        val confirmed = confirmedSettlement()
        assertThatThrownBy {
            confirmed.returnForAdjustment(OPERATOR, ROLE, "too late", NOW.plusMinutes(1))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("不能退回调整")
    }

    @Test
    @DisplayName("PAY-AC-062/068 — effective ownership 幂等")
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

        replacement.voidBeforeExecution(OPERATOR, ROLE, "replacement cancelled", NOW.plusMinutes(1), "ledger-1")
        assertThatThrownBy { replacement.activateEffectiveOwnership() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("不能激活有效所有权")
    }

    @Test
    @DisplayName("PAY-AC-061/096 — 排除候选有稳定原因且不进入金额或消费身份")
    fun `excluded candidate remains in frozen detail without changing money or ownership`() {
        val settlement = settlement("127.00", withExcluded = true)
        val excluded = settlement.settlementLines.single { it.decision == "EXCLUDED" }

        assertThat(settlement.eligibleCount).isEqualTo(1)
        assertThat(settlement.excludedCount).isEqualTo(1)
        assertThat(settlement.netAmount).isEqualByComparingTo("127.00")
        assertThat(excluded.sourceFactIdentity).isEqualTo("FACT-EXCLUDED")
        assertThat(excluded.reasonCode).isEqualTo("UNRESOLVED_RECONCILIATION")
        assertThat(excluded.effectiveConsumptionIdentity).isNull()

        settlement.confirmComposition(OPERATOR, ROLE, NOW, "reviewed", "ledger-1")
        assertThat(settlement.compositionFrozen).isTrue()
        assertThat(settlement.netAmount).isEqualByComparingTo("127.00")
        assertThat(excluded.effectiveConsumptionIdentity).isNull()
    }

    @Test
    @DisplayName("PAY-AC-086 — 最小领域操作员权限守卫（非完整 RBAC）")
    fun `only authorized operators may confirm adjudicate or void`() {
        val prepared = settlement("10.00")
        assertThatThrownBy { prepared.confirmComposition(OPERATOR, "VIEWER", NOW, "reviewed", "ledger-1") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("无权处理商户结算")

        prepared.voidBeforeExecution(OPERATOR, ROLE, "merchant correction", NOW, "ledger-1")
        assertThat(prepared.status).isEqualTo(MerchantSettlementStatus.VOIDED)
        assertThat(prepared.effectiveScopeIdentity).isNull()
        assertThat(prepared.settlementLines.single().effectiveConsumptionIdentity).isNull()
        val replacementId = MerchantSettlementId.parse("018f22a0-0000-7000-8000-000000000099")
        prepared.linkReplacement(replacementId)
        assertThat(prepared.replacementSettlementId).isEqualTo(replacementId)

        val confirmed = confirmedSettlement()
        assertThatThrownBy { confirmed.voidBeforeExecution(OPERATOR, ROLE, "too late", NOW.plusMinutes(1), "ledger-1") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("不能作废")
    }

    private fun confirmedSettlement(): MerchantSettlement = settlement("127.00").also {
        it.confirmComposition(OPERATOR, ROLE, NOW, "reviewed", "ledger-1")
    }

    private fun MerchantSettlement.startAttempt(requestIdentity: String = "REQ-1"): SettlementExecutionAttempt =
        startExecutionAttempt(
            operatorIdentity = OPERATOR,
            operatorRole = ROLE,
            requestedAt = NOW.plusMinutes(1),
            reviewAfterMinutes = 30,
            executionGroupIdentity = "GROUP-1",
            requestIdentity = requestIdentity,
            executionChannelId = "C-001",
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

    private fun settlement(net: String, withExcluded: Boolean = false): MerchantSettlement {
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
                executionChannelId = null,
                currency = "CNY",
                periodStart = NOW.toLocalDate().atStartOfDay(),
                periodEnd = NOW.toLocalDate().plusDays(1).atStartOfDay(),
                businessTimezone = "Asia/Shanghai",
                scopeIdentity = "SCOPE-1",
                effectiveScopeIdentity = "SCOPE-1",
                status = MerchantSettlementStatus.PREPARED,
                eligibleCount = 1,
                excludedCount = if (withExcluded) 1 else 0,
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
                settlementLines = listOf(line) + if (withExcluded) listOf(
                    line.copy(
                        lineIdentity = "LINE-EXCLUDED",
                        sourceFactIdentity = "FACT-EXCLUDED",
                        decision = "EXCLUDED",
                        reasonCode = "UNRESOLVED_RECONCILIATION",
                        effectiveConsumptionIdentity = null,
                        signedNetAmount = BigDecimal("500.00"),
                    )
                ) else emptyList(),
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
