package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toContractMoney
import com.only4.cap4k.reference.payment.application.queries.reconciliation.bill.GetAuthoritativeBillQry
import com.only4.cap4k.reference.payment.application.queries.reconciliation.bill.ListBillRevisionsQry
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.AuthoritativeBillRecordView
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.AuthoritativeBillRevisionView
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.GetAuthoritativeBillEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.ListBillRevisionsEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBillId
import org.springframework.stereotype.Component

@Component
class GetAuthoritativeBillEndpointHandler : EndpointHandler<GetAuthoritativeBillEndpoint.Request, GetAuthoritativeBillEndpoint.Response> {
    override fun handle(request: GetAuthoritativeBillEndpoint.Request): GetAuthoritativeBillEndpoint.Response =
        Mediator.queries.ask(GetAuthoritativeBillQry.Request(AuthoritativeBillId.parse(request.billId))).let { bill ->
            GetAuthoritativeBillEndpoint.Response(
                billId = bill.billId,
                billIdentity = bill.billIdentity,
                channelId = bill.channelId,
                currency = bill.currency,
                businessDate = bill.businessDate,
                businessTimezone = bill.businessTimezone,
                createdAt = bill.createdAt,
                currentRevision = bill.currentRevision,
                currentRevisionId = bill.currentRevisionId,
                revisions = bill.toContractRevisions(),
            )
        }
}

@Component
class ListBillRevisionsEndpointHandler : EndpointHandler<ListBillRevisionsEndpoint.Request, ListBillRevisionsEndpoint.Response> {
    override fun handle(request: ListBillRevisionsEndpoint.Request): ListBillRevisionsEndpoint.Response =
        Mediator.queries.ask(ListBillRevisionsQry.Request(AuthoritativeBillId.parse(request.billId))).bill.let { bill ->
            ListBillRevisionsEndpoint.Response(
                billId = bill.billId,
                billIdentity = bill.billIdentity,
                channelId = bill.channelId,
                currency = bill.currency,
                businessDate = bill.businessDate,
                businessTimezone = bill.businessTimezone,
                createdAt = bill.createdAt,
                currentRevision = bill.currentRevision,
                currentRevisionId = bill.currentRevisionId,
                revisions = bill.toContractRevisions(),
            )
        }
}

private fun GetAuthoritativeBillQry.Response.toContractRevisions(): List<AuthoritativeBillRevisionView> =
    revisions.map { revision ->
        AuthoritativeBillRevisionView(
            revisionId = revision.revisionId,
            revision = revision.revision,
            publishedAt = revision.publishedAt,
            completeness = revision.completeness,
            rawEvidence = revision.rawEvidence,
            payloadFingerprint = revision.payloadFingerprint,
            records = revision.records.map { record ->
                AuthoritativeBillRecordView(
                    recordId = record.recordId,
                    recordIdentity = record.recordIdentity,
                    transactionKind = record.transactionKind,
                    externalTransactionIdentity = record.externalTransactionIdentity,
                    money = record.amount.toContractMoney(record.currency),
                    rawStatus = record.rawStatus,
                    occurredAt = record.occurredAt,
                    receivedAt = record.receivedAt,
                    rawEvidence = record.rawEvidence,
                )
            },
        )
    }
