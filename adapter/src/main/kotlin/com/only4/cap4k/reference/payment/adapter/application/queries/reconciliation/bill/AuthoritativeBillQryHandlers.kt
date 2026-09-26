package com.only4.cap4k.reference.payment.adapter.application.queries.reconciliation.bill

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.reference.payment.application.errors.AuthoritativeBillNotFoundException
import com.only4.cap4k.reference.payment.application.queries.reconciliation.bill.GetAuthoritativeBillQry
import com.only4.cap4k.reference.payment.application.queries.reconciliation.bill.ListBillRevisionsQry
import com.only4.cap4k.reference.payment.domain._share.meta.authoritative_bill.SAuthoritativeBill
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBillId
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "GetAuthoritativeBill",
    packageName = "reconciliation.bill",
    description = "Read the authoritative bill and its complete immutable revision history",
    aggregates = ["AuthoritativeBill"],
    family = "query-handler",
)
class GetAuthoritativeBillQryHandler : QueryHandler<GetAuthoritativeBillQry.Request, GetAuthoritativeBillQry.Response> {
    override fun handle(query: GetAuthoritativeBillQry.Request): GetAuthoritativeBillQry.Response =
        readAuthoritativeBill(query.billId)
}

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "ListBillRevisions",
    packageName = "reconciliation.bill",
    description = "List every persisted authoritative bill revision in ascending revision order",
    aggregates = ["AuthoritativeBill"],
    family = "query-handler",
)
class ListBillRevisionsQryHandler : QueryHandler<ListBillRevisionsQry.Request, ListBillRevisionsQry.Response> {
    override fun handle(query: ListBillRevisionsQry.Request): ListBillRevisionsQry.Response =
        ListBillRevisionsQry.Response(readAuthoritativeBill(query.billId))
}

private fun readAuthoritativeBill(billId: AuthoritativeBillId): GetAuthoritativeBillQry.Response {
    val bill = Mediator.repositories.findOne(SAuthoritativeBill.predicateById(billId))
        ?: throw AuthoritativeBillNotFoundException(billId)
    return GetAuthoritativeBillQry.Response(
        billId = bill.id.toString(),
        billIdentity = bill.billIdentity,
        channelId = bill.channelId,
        currency = bill.currency,
        businessDate = bill.businessDate,
        businessTimezone = bill.businessTimezone,
        createdAt = requireNotNull(bill.createdAt) { "权威账单缺少创建时间" }.toInstant(ZoneOffset.UTC),
        currentRevision = bill.currentRevision,
        currentRevisionId = bill.currentRevisionId,
        revisions = bill.billRevisions.sortedBy { it.revision.toBigInteger() }.map { revision ->
            GetAuthoritativeBillQry.Response.Revision(
                revisionId = revision.id.toString(),
                revision = revision.revision,
                publishedAt = revision.publishedAt.toInstant(ZoneOffset.UTC),
                completeness = revision.completeness.name,
                rawEvidence = revision.rawEvidence,
                payloadFingerprint = revision.payloadFingerprint,
                records = revision.records.sortedBy { it.recordIdentity }.map { record ->
                    GetAuthoritativeBillQry.Response.Record(
                        recordId = record.id.toString(),
                        recordIdentity = record.recordIdentity,
                        transactionKind = record.transactionKind.name,
                        externalTransactionIdentity = record.channelTransactionIdentity,
                        amount = record.amount,
                        currency = record.currency,
                        rawStatus = record.rawStatus,
                        occurredAt = record.occurredAt?.toInstant(ZoneOffset.UTC),
                        receivedAt = record.receivedAt.toInstant(ZoneOffset.UTC),
                        rawEvidence = record.rawEvidence,
                    )
                },
            )
        },
    )
}
