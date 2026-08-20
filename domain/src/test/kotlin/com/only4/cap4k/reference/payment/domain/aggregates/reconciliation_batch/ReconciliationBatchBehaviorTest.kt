package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch

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
import org.junit.jupiter.api.Test

class ReconciliationBatchBehaviorTest {
    @Test
    fun `classifies matched payment and every required difference without overwriting either snapshot`() {
        val facts = listOf(
            fact("matched", "tx-matched"),
            fact("platform-only", "tx-platform-only"),
            fact("amount", "tx-amount"),
            fact("currency", "tx-currency"),
            fact("status", "tx-status"),
            fact("kind", "tx-kind"),
        )
        val records = listOf(
            record("matched", "tx-matched"),
            record("channel-only", "tx-channel-only"),
            record("amount", "tx-amount", amount = "99.00"),
            record("currency", "tx-currency", currency = "USD"),
            record("status", "tx-status", status = "FAILED"),
            record("kind", "tx-kind", kind = ReconciliationTransactionKind.REFUND),
            record("duplicate", "tx-duplicate"),
            record("duplicate-2", "tx-duplicate"),
        )

        val result = batch().appendTestRun(statement(records = records), facts, NOW)
        val items = result.run.reconciliationItems.toList()

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
    fun `same statement identity and revision is idempotent while a new revision supersedes and retains history`() {
        val batch = batch()
        val first = batch.appendTestRun(statement(revision = "1"), listOf(fact("matched", "tx-matched")), NOW)
        val replay = batch.appendTestRun(statement(revision = "1"), listOf(fact("different", "tx-other")), NOW.plusMinutes(1))
        val revised = batch.appendTestRun(statement(revision = "2"), listOf(fact("matched", "tx-matched")), NOW.plusMinutes(2))

        assertThat(replay.idempotentReplay).isTrue()
        assertThat(replay.run).isSameAs(first.run)
        assertThat(batch.reconciliationRuns).hasSize(2)
        assertThat(first.run.status).isEqualTo(ReconciliationRunStatus.SUPERSEDED)
        assertThat(first.run.reconciliationItems).hasSize(1)
        assertThat(revised.run.status).isEqualTo(ReconciliationRunStatus.COMPLETED)
        assertThat(batch.currentEffectiveRunId).isEqualTo(revised.run.id.toString())
        assertThat(batch.currentEffectiveRunId).isNotEqualTo(first.run.id.toString())
    }

    @Test
    fun `incomplete statement and unresolved differences block completion`() {
        val incomplete = batch()
        incomplete.appendTestRun(
            statement(completeness = StatementCompleteness.INCOMPLETE),
            listOf(fact("matched", "tx-matched")), NOW,
        )
        assertThat(incomplete.status).isEqualTo(ReconciliationBatchStatus.REVIEW_REQUIRED)
        assertThat(incomplete.settlementBlocked).isTrue()
        assertThat(incomplete.blockingReason).isEqualTo("Statement is not complete")

        val unresolved = batch()
        unresolved.appendTestRun(statement(records = emptyList()), listOf(fact("platform-only", "tx-only")), NOW)
        assertThat(unresolved.status).isEqualTo(ReconciliationBatchStatus.AWAITING_DISPOSITION)
        assertThat(unresolved.completedAt).isNull()
        assertThat(unresolved.unresolvedDifferenceCount).isEqualTo(1)
    }

    @Test
    fun `denied disposition is retained but does not resolve the difference`() {
        val batch = batch()
        val run = batch.appendTestRun(statement(records = emptyList()), listOf(fact("only", "tx-only")), NOW).run
        val item = run.reconciliationItems.single()

        batch.appendDisposition(item.differenceIdentity, disposition(DispositionAuthorization.DENIED, ReconciliationDispositionStatus.REJECTED))

        assertThat(item.reconciliationDispositions).hasSize(1)
        assertThat(item.reconciliationDispositions.single().authorizationResult).isEqualTo(DispositionAuthorization.DENIED)
        assertThat(item.resolved).isFalse()
        assertThat(batch.status).isEqualTo(ReconciliationBatchStatus.AWAITING_DISPOSITION)
        assertThat(batch.unresolvedDifferenceCount).isEqualTo(1)
    }

    @Test
    fun `authorized disposition resolves difference and appends a confirmation fact`() {
        val batch = batch()
        val run = batch.appendTestRun(
            statement(records = listOf(record("channel-only", "tx-confirm"))), emptyList(), NOW,
        ).run
        val item = run.reconciliationItems.single()
        val confirmation = ReconciliationConfirmationFactCreation(
            sourceDifferenceIdentity = item.differenceIdentity,
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
            disposition(
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
    fun `authorized disposition requires an explicit conclusion and confirmation consistency`() {
        val batch = batch()
        val item = batch.appendTestRun(
            statement(records = listOf(record("channel-only", "tx-confirm"))),
            emptyList(),
            NOW,
        ).run.reconciliationItems.single()

        org.assertj.core.api.Assertions.assertThatThrownBy {
            batch.appendDisposition(
                item.differenceIdentity,
                disposition(
                    authorization = DispositionAuthorization.AUTHORIZED,
                    status = ReconciliationDispositionStatus.APPLIED,
                ).copy(conclusion = null),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("declare a conclusion")

        org.assertj.core.api.Assertions.assertThatThrownBy {
            batch.appendDisposition(
                item.differenceIdentity,
                disposition(
                    authorization = DispositionAuthorization.AUTHORIZED,
                    status = ReconciliationDispositionStatus.APPLIED,
                    conclusion = ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT,
                    impact = SettlementImpact.CONFIRMS_SETTLEMENT_FACT,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("requires a confirmation fact")
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

    private fun batch() = ReconciliationBatchFactory().create(
        ReconciliationBatchFactory.Payload(
            channelId = "C-001", currency = "CNY", reconciliationDate = DATE,
            businessTimezone = "Asia/Shanghai", status = ReconciliationBatchStatus.PENDING,
            currentEffectiveRunId = null, statementWaitDeadlineAt = DATE.atStartOfDay().plusDays(2),
            blockingReason = "Awaiting statement", completedAt = null,
        )
    )

    private fun statement(
        revision: String = "1",
        completeness: StatementCompleteness = StatementCompleteness.COMPLETE,
        records: List<ChannelStatementRecord> = listOf(record("matched", "tx-matched")),
    ) = ChannelStatement("C-001", "CNY", DATE, "Asia/Shanghai", "statement-1", revision, completeness, INSTANT, records)

    private fun record(
        id: String, tx: String, amount: String = "100.00", currency: String = "CNY",
        status: String = "SUCCESS", kind: ReconciliationTransactionKind = ReconciliationTransactionKind.PAYMENT,
    ) = ChannelStatementRecord(id, kind, tx, BigDecimal(amount), currency, status, INSTANT, INSTANT.plusSeconds(1))

    private fun fact(
        id: String, tx: String, amount: String = "100.00", currency: String = "CNY",
        status: String = "SUCCESS", kind: ReconciliationTransactionKind = ReconciliationTransactionKind.PAYMENT,
    ) = PlatformReconciliationFact(id, kind, "payment-$id", "attempt-$id", null, null, tx, BigDecimal(amount), currency, status, INSTANT, INSTANT.plusSeconds(2))

    private fun disposition(
        authorization: DispositionAuthorization,
        status: ReconciliationDispositionStatus,
        conclusion: ReconciliationDispositionConclusion = ReconciliationDispositionConclusion.NO_SETTLEMENT_IMPACT,
        impact: SettlementImpact = SettlementImpact.DOES_NOT_BLOCK_SETTLEMENT,
    ) = ReconciliationDispositionCreation(
        operatorIdentity = "finance-1", operatorRole = "FINANCE_OPERATOR", authorizationResult = authorization,
        status = status, conclusion = conclusion, settlementImpact = impact, evidence = "ticket-1",
        followUp = "none", disposedAt = NOW.plusMinutes(5),
    )

    private var runSequence: Int = 1

    companion object {
        private val DATE: LocalDate = LocalDate.parse("2026-08-18")
        private val NOW: LocalDateTime = LocalDateTime.parse("2026-08-19T09:00:00")
        private val INSTANT: Instant = Instant.parse("2026-08-18T01:00:00Z")
    }
}
