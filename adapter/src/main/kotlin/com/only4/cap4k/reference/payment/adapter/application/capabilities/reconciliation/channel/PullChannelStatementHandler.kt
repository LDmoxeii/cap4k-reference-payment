package com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceBillReadScriptRegistry
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.PullChannelStatement
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.ChannelStatementUnavailableException
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBill
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.currentBillRevision
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatementRecord
import jakarta.persistence.EntityManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "PullChannelStatement",
    packageName = "reconciliation.channel",
    description = "Pull one immutable revisioned channel statement for a reconciliation scope",
    aggregates = ["ReconciliationBatch"],
    family = "capability-handler"
)
class PullChannelStatementHandler(
    private val statements: ChannelStatementFixtureStore,
    private val entityManager: EntityManager,
    private val clock: Clock,
    private val billReadScripts: ReferenceBillReadScriptRegistry,
) : CapabilityHandler<PullChannelStatement.Request, PullChannelStatement.Response> {

    override fun call(request: PullChannelStatement.Request): PullChannelStatement.Response {
        val statement = pullAuthoritativeBill(request) ?: statements.latest(request.channelId, request.currency, request.reconciliationDate)
        require(statement.businessTimezone == request.businessTimezone) {
            "账单时区 ${statement.businessTimezone} 与请求时区 ${request.businessTimezone} 不一致"
        }
        return PullChannelStatement.Response(statement)
    }

    private fun pullAuthoritativeBill(request: PullChannelStatement.Request): ChannelStatement? {
        val requestedStatementIdentity = request.statementIdentity
        val query = if (requestedStatementIdentity.isNullOrBlank()) {
            entityManager.createQuery(
                "select b from AuthoritativeBill b where b.channelId = :channelId and b.currency = :currency and b.businessDate = :businessDate order by b.createdAt desc",
                AuthoritativeBill::class.java,
            )
                .setParameter("channelId", request.channelId)
                .setParameter("currency", request.currency.uppercase())
                .setParameter("businessDate", request.reconciliationDate)
        } else {
            val statementIdentity = requestedStatementIdentity.trim()
            entityManager.createQuery(
                "select b from AuthoritativeBill b where b.channelId = :channelId and b.currency = :currency and b.businessDate = :businessDate and b.billIdentity = :billIdentity",
                AuthoritativeBill::class.java,
            )
                .setParameter("channelId", request.channelId)
                .setParameter("currency", request.currency.uppercase())
                .setParameter("businessDate", request.reconciliationDate)
                .setParameter("billIdentity", statementIdentity)
        }
        val bill = query.setMaxResults(1).resultList.firstOrNull() ?: return null
        if (billReadScripts.consumeUnavailableRead(bill.channelId, bill.billIdentity)) {
            throw ChannelStatementUnavailableException(request.channelId, request.currency, request.reconciliationDate)
        }
        val revision = bill.currentBillRevision()
            ?: throw ChannelStatementUnavailableException(request.channelId, request.currency, request.reconciliationDate)
        val fetchedAt = Instant.now(clock)
        return ChannelStatement(
            channelId = bill.channelId,
            currency = bill.currency,
            reconciliationDate = bill.businessDate,
            businessTimezone = bill.businessTimezone,
            statementIdentity = bill.billIdentity,
            statementRevision = revision.revision,
            completeness = revision.completeness,
            fetchedAt = fetchedAt,
            records = revision.records.map { record ->
                ChannelStatementRecord(
                    recordIdentity = record.recordIdentity,
                    transactionKind = record.transactionKind,
                    channelTransactionIdentity = record.channelTransactionIdentity,
                    amount = record.amount,
                    currency = record.currency,
                    rawStatus = record.rawStatus,
                    occurredAt = record.occurredAt?.toInstant(ZoneOffset.UTC) ?: fetchedAt,
                    receivedAt = record.receivedAt.toInstant(ZoneOffset.UTC),
                )
            },
        )
    }
}
