package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch

import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.*
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.factory.ReconciliationBatchFactory
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatementRecord
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.PlatformReconciliationFact
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReconciliationBatchBehaviorTest {
    @Test
    @DisplayName("PAY-AC-040/043 — 对账分类与双方事实快照")
    fun `classifies matched payment and every required difference without overwriting either snapshot`() {
        // Given：平台事实与渠道账单同时包含匹配、单边、金额/币种/状态/类型差异和重复记录。
        val facts = listOf(
            givenPlatformFact("matched", "tx-matched"),
            givenPlatformFact("platform-only", "tx-platform-only"),
            givenPlatformFact("amount", "tx-amount"),
            givenPlatformFact("currency", "tx-currency"),
            givenPlatformFact("status", "tx-status"),
            givenPlatformFact("kind", "tx-kind"),
        )
        val records = listOf(
            givenStatementRecord("matched", "tx-matched"),
            givenStatementRecord("channel-only", "tx-channel-only"),
            givenStatementRecord("amount", "tx-amount", amount = "99.00"),
            givenStatementRecord("currency", "tx-currency", currency = "USD"),
            givenStatementRecord("status", "tx-status", status = "FAILED"),
            givenStatementRecord("kind", "tx-kind", kind = ReconciliationTransactionKind.REFUND),
            givenStatementRecord("duplicate", "tx-duplicate"),
            givenStatementRecord("duplicate-2", "tx-duplicate"),
        )

        // When：追加一次真实对账 run，由聚合根据外部 identity 和业务字段分类差异。
        val result = givenPendingBatch().appendTestRun(givenCompleteStatement(records = records), facts, NOW)
        val items = result.run.reconciliationItems.toList()

        // Then：断言同时覆盖 difference 分类、双方快照、matched/unresolved 计数，避免只看最终状态。
        assertThat(items.map { it.differenceType }).containsExactlyInAnyOrder(
            ReconciliationDifferenceType.MATCHED,
            ReconciliationDifferenceType.PLATFORM_ONLY,
            ReconciliationDifferenceType.CHANNEL_ONLY,
            ReconciliationDifferenceType.AMOUNT_MISMATCH,
            ReconciliationDifferenceType.CURRENCY_MISMATCH,
            ReconciliationDifferenceType.STATUS_MISMATCH,
            ReconciliationDifferenceType.UNMATCHED,
            ReconciliationDifferenceType.CHANNEL_ONLY,
            ReconciliationDifferenceType.DUPLICATE_CHANNEL_RECORD,
        )
        val mismatch = items.single { it.differenceType == ReconciliationDifferenceType.AMOUNT_MISMATCH }
        assertThat(mismatch.platformAmount).isEqualByComparingTo("100.00")
        assertThat(mismatch.channelAmount).isEqualByComparingTo("99.00")
        assertThat(items.single { it.differenceType == ReconciliationDifferenceType.MATCHED }.resolved).isTrue()
        assertThat(result.run.matchedCount).isEqualTo(1)
        assertThat(result.run.differenceCount).isEqualTo(8)
        assertThat(result.run.unresolvedDifferenceCount).isEqualTo(8)
    }

    @Test
    @DisplayName("PAY-AC-041/046 — review blocker 保持未决")
    fun `matched payment with blocking review remains unresolved and preserves the run snapshot`() {
        val blockedFact = givenPlatformFact("matched-review", "tx-matched-review").copy(
            paymentReviewIdentitySnapshot = "payment-review:one,payment-review:two",
            paymentReviewSummary = "late success requires authorized review",
            settlementEligible = false,
        )

        val batch = givenPendingBatch()
        val result = batch.appendTestRun(
            givenCompleteStatement(records = listOf(givenStatementRecord("matched-review", "tx-matched-review"))),
            listOf(blockedFact),
            NOW,
        )
        val item = result.run.reconciliationItems.single()

        assertThat(item.differenceType).isEqualTo(ReconciliationDifferenceType.MATCHED)
        assertThat(item.resolved).isFalse()
        assertThat(item.settlementBlocked).isTrue()
        assertThat(item.paymentReviewIdentitySnapshot).isEqualTo("payment-review:one,payment-review:two")
        assertThat(item.paymentReviewSummary).isEqualTo("late success requires authorized review")
        assertThat(result.run.unresolvedDifferenceCount).isEqualTo(1)
        assertThat(result.run.status).isEqualTo(ReconciliationRunStatus.RECONCILING)
        assertThat(result.run.completedAt).isNull()
        assertThat(batch.status).isEqualTo(ReconciliationBatchStatus.AWAITING_DISPOSITION)
    }

    @Test
    @DisplayName("PAY-AC-044/045/082 — revision 幂等、effective run 与追加历史")
    fun `same statement identity and revision is idempotent while a new revision supersedes and retains history`() {
        val batch = givenPendingBatch()
        val first = batch.appendTestRun(givenCompleteStatement(revision = "2"), listOf(givenPlatformFact("matched", "tx-matched")), NOW)
        val replay = batch.appendTestRun(givenCompleteStatement(revision = "2"), listOf(givenPlatformFact("different", "tx-other")), NOW.plusMinutes(1))
        val revised = batch.appendTestRun(givenCompleteStatement(revision = "3"), listOf(givenPlatformFact("matched", "tx-matched")), NOW.plusMinutes(2))
        val lateOlder = batch.appendTestRun(givenCompleteStatement(revision = "1"), listOf(givenPlatformFact("older", "tx-older")), NOW.plusMinutes(3))

        assertThat(replay.idempotentReplay).isTrue()
        assertThat(replay.run).isSameAs(first.run)
        assertThat(batch.reconciliationRuns).hasSize(3)
        assertThat(first.run.status).isEqualTo(ReconciliationRunStatus.SUPERSEDED)
        assertThat(first.run.reconciliationItems).hasSize(1)
        assertThat(revised.run.status).isEqualTo(ReconciliationRunStatus.COMPLETED)
        assertThat(lateOlder.run.status).isEqualTo(ReconciliationRunStatus.SUPERSEDED)
        assertThat(batch.currentEffectiveRunId).isEqualTo(revised.run.id.toString())
        assertThat(batch.currentEffectiveRunId).isNotEqualTo(first.run.id.toString())
        assertThat(batch.currentEffectiveRunId).isNotEqualTo(lateOlder.run.id.toString())
    }

    @Test
    @DisplayName("PAY-AC-046 — 不完整账单与未决差异阻断完成")
    fun `incomplete statement and unresolved differences block completion`() {
        val incomplete = givenPendingBatch()
        incomplete.appendTestRun(
            givenCompleteStatement(completeness = StatementCompleteness.INCOMPLETE),
            listOf(givenPlatformFact("matched", "tx-matched")), NOW,
        )
        assertThat(incomplete.status).isEqualTo(ReconciliationBatchStatus.REVIEW_REQUIRED)
        assertThat(incomplete.settlementBlocked).isTrue()
        assertThat(incomplete.blockingReason).isEqualTo("渠道账单不完整")

        val unresolved = givenPendingBatch()
        val unresolvedRun = unresolved.appendTestRun(
            givenCompleteStatement(records = emptyList()),
            listOf(givenPlatformFact("platform-only", "tx-only")),
            NOW,
        ).run
        assertThat(unresolved.status).isEqualTo(ReconciliationBatchStatus.AWAITING_DISPOSITION)
        assertThat(unresolved.completedAt).isNull()
        assertThat(unresolved.unresolvedDifferenceCount).isEqualTo(1)
        assertThat(unresolvedRun.status).isEqualTo(ReconciliationRunStatus.RECONCILING)
        assertThat(unresolvedRun.completedAt).isNull()
    }

    @Test
    @DisplayName("PAY-AC-047/082 — 被拒处置留痕但不改写事实")
    fun `denied disposition is retained but does not resolve the difference`() {
        val batch = givenPendingBatch()
        val run = batch.appendTestRun(givenCompleteStatement(records = emptyList()), listOf(givenPlatformFact("only", "tx-only")), NOW).run
        val item = run.reconciliationItems.single()

        batch.appendDisposition(item.differenceIdentity, givenDisposition(DispositionAuthorization.DENIED, ReconciliationDispositionStatus.REJECTED))

        assertThat(item.reconciliationDispositions).hasSize(1)
        assertThat(item.reconciliationDispositions.single().authorizationResult).isEqualTo(DispositionAuthorization.DENIED)
        assertThat(item.resolved).isFalse()
        assertThat(batch.status).isEqualTo(ReconciliationBatchStatus.AWAITING_DISPOSITION)
        assertThat(batch.unresolvedDifferenceCount).isEqualTo(1)
    }

    @Test
    @DisplayName("PAY-AC-042/047/082 — 授权处置追加确认事实")
    fun `authorized disposition resolves difference and appends a confirmation fact`() {
        val batch = givenPendingBatch()
        val run = batch.appendTestRun(
            givenCompleteStatement(records = listOf(givenStatementRecord("channel-only", "tx-confirm"))), emptyList(), NOW,
        ).run
        val item = run.reconciliationItems.single()
        val confirmation = ReconciliationConfirmationFactCreation(
            sourceDifferenceIdentity = item.differenceIdentity,
            merchantId = "M-001",
            channelId = "C-001",
            operatorIdentity = "finance-1",
            confirmationReason = "Channel success proves omitted platform success",
            evidence = "statement://s-1/1/channel-only",
            transactionKind = ReconciliationTransactionKind.PAYMENT,
            amount = BigDecimal("100.00"), currency = "CNY",
            externalTransactionIdentity = "tx-confirm", paymentId = null, refundId = null,
            confirmedAt = NOW.plusMinutes(5),
        )

        batch.appendDisposition(
            item.differenceIdentity,
            givenDisposition(
                authorization = DispositionAuthorization.AUTHORIZED,
                status = ReconciliationDispositionStatus.APPLIED,
                conclusion = ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT,
                impact = SettlementImpact.CONFIRMS_SETTLEMENT_FACT,
            ),
            confirmation,
        )

        assertThat(item.resolved).isTrue()
        assertThat(item.settlementBlocked).isFalse()
        assertThat(item.reconciliationConfirmationFacts).hasSize(1)
        assertThat(item.reconciliationConfirmationFacts.single().sourceDifferenceIdentity).isEqualTo(item.differenceIdentity)
        assertThat(batch.status).isEqualTo(ReconciliationBatchStatus.COMPLETED)
        assertThat(batch.settlementBlocked).isFalse()
        assertThat(batch.completedAt).isEqualTo(NOW.plusMinutes(5))
    }

    @Test
    @DisplayName("PAY-AC-042/047 — 授权处置的结论与确认一致性")
    fun `authorized disposition requires an explicit conclusion and confirmation consistency`() {
        val batch = givenPendingBatch()
        val item = batch.appendTestRun(
            givenCompleteStatement(records = listOf(givenStatementRecord("channel-only", "tx-confirm"))),
            emptyList(),
            NOW,
        ).run.reconciliationItems.single()

        org.assertj.core.api.Assertions.assertThatThrownBy {
            batch.appendDisposition(
                item.differenceIdentity,
                givenDisposition(
                    authorization = DispositionAuthorization.AUTHORIZED,
                    status = ReconciliationDispositionStatus.APPLIED,
                ).copy(conclusion = null),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("必须声明结论")

        org.assertj.core.api.Assertions.assertThatThrownBy {
            batch.appendDisposition(
                item.differenceIdentity,
                givenDisposition(
                    authorization = DispositionAuthorization.AUTHORIZED,
                    status = ReconciliationDispositionStatus.APPLIED,
                    conclusion = ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT,
                    impact = SettlementImpact.CONFIRMS_SETTLEMENT_FACT,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("必须同时提供确认事实")
    }

    private fun ReconciliationBatch.appendTestRun(
        statement: ChannelStatement,
        platformFacts: List<PlatformReconciliationFact>,
        startedAt: LocalDateTime,
    ): ReconciliationRunResult {
        val suffix = runSequence++.toString().padStart(12, '0')
        return appendReconciliationRun(
            statement = statement,
            platformFacts = platformFacts,
            startedAt = startedAt,
            runId = ReconciliationRunId.parse("018f22a0-0000-7000-8000-$suffix"),
        )
    }

    private fun givenPendingBatch() = ReconciliationBatchFactory().create(
        ReconciliationBatchFactory.Payload(
            channelId = "C-001", currency = "CNY", reconciliationDate = DATE,
            businessTimezone = "Asia/Shanghai", status = ReconciliationBatchStatus.PENDING,
            currentEffectiveRunId = null, statementWaitDeadlineAt = DATE.atStartOfDay().plusDays(2),
            blockingReason = "Awaiting statement", completedAt = null,
        )
    )

    private fun givenCompleteStatement(
        revision: String = "1",
        completeness: StatementCompleteness = StatementCompleteness.COMPLETE,
        records: List<ChannelStatementRecord> = listOf(givenStatementRecord("matched", "tx-matched")),
    ) = ChannelStatement("C-001", "CNY", DATE, "Asia/Shanghai", "statement-1", revision, completeness, INSTANT, records)

    private fun givenStatementRecord(
        id: String, tx: String, amount: String = "100.00", currency: String = "CNY",
        status: String = "SUCCESS", kind: ReconciliationTransactionKind = ReconciliationTransactionKind.PAYMENT,
    ) = ChannelStatementRecord(id, kind, tx, BigDecimal(amount), currency, status, INSTANT, INSTANT.plusSeconds(1))

    private fun givenPlatformFact(
        id: String, tx: String, amount: String = "100.00", currency: String = "CNY",
        status: String = "SUCCESS", kind: ReconciliationTransactionKind = ReconciliationTransactionKind.PAYMENT,
    ) = PlatformReconciliationFact(
        id, kind, PaymentId.parse("018f22a0-0000-7000-8000-000000000020"), "attempt-$id", null, null,
        tx, BigDecimal(amount), currency, status, INSTANT, INSTANT.plusSeconds(2),
    )

    private fun givenDisposition(
        authorization: DispositionAuthorization,
        status: ReconciliationDispositionStatus,
        conclusion: ReconciliationDispositionConclusion = ReconciliationDispositionConclusion.NO_SETTLEMENT_IMPACT,
        impact: SettlementImpact = SettlementImpact.DOES_NOT_BLOCK_SETTLEMENT,
    ) = ReconciliationDispositionCreation(
        operatorIdentity = "finance-1", operatorRole = "FINANCE_OPERATOR", authorizationResult = authorization,
        status = status, conclusion = conclusion, settlementImpact = impact, reason = "verified difference",
        evidence = "ticket-1",
        followUp = "none", disposedAt = NOW.plusMinutes(5),
    )

    private var runSequence: Int = 1

    companion object {
        private val DATE: LocalDate = LocalDate.parse("2026-08-18")
        private val NOW: LocalDateTime = LocalDateTime.parse("2026-08-19T09:00:00")
        private val INSTANT: Instant = Instant.parse("2026-08-18T01:00:00Z")
    }
}
