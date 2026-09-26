package com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant
import java.time.LocalDate

data class AuthoritativeBillRevisionView(
    val revisionId: String,
    val revision: String,
    val publishedAt: Instant,
    val completeness: String,
    val rawEvidence: String,
    val payloadFingerprint: String,
    val records: List<AuthoritativeBillRecordView>,
)

data class AuthoritativeBillRecordView(
    val recordId: String,
    val recordIdentity: String,
    val transactionKind: String,
    val externalTransactionIdentity: String,
    val money: Money,
    val rawStatus: String,
    val occurredAt: Instant?,
    val receivedAt: Instant,
    val rawEvidence: String,
)

@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetAuthoritativeBillEndpoint",
    packageName = "reconciliation.api",
    description = "GET /api/authoritative-bills/{billId}",
    aggregates = [],
    operationName = "authoritative-bill.get",
    family = "endpoint",
)
object GetAuthoritativeBillEndpoint {
    const val OPERATION_NAME = "authoritative-bill.get"
    data class Request(val billId: String) : EndpointRequest<Response>
    data class Response(
        val billId: String,
        val billIdentity: String,
        val channelId: String,
        val currency: String,
        val businessDate: LocalDate,
        val businessTimezone: String,
        val createdAt: Instant,
        val currentRevision: String?,
        val currentRevisionId: String?,
        val revisions: List<AuthoritativeBillRevisionView>,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ListBillRevisionsEndpoint",
    packageName = "reconciliation.api",
    description = "GET /api/authoritative-bills/{billId}/revisions",
    aggregates = [],
    operationName = "authoritative-bill.revisions.list",
    family = "endpoint",
)
object ListBillRevisionsEndpoint {
    const val OPERATION_NAME = "authoritative-bill.revisions.list"
    data class Request(val billId: String) : EndpointRequest<Response>
    data class Response(
        val billId: String,
        val billIdentity: String,
        val channelId: String,
        val currency: String,
        val businessDate: LocalDate,
        val businessTimezone: String,
        val createdAt: Instant,
        val currentRevision: String?,
        val currentRevisionId: String?,
        val revisions: List<AuthoritativeBillRevisionView>,
    )
}
