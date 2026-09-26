package com.only4.cap4k.reference.payment.application.queries.reconciliation.bill

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBillId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@DesignBlockMetadata(
    tag = "query",
    name = "GetAuthoritativeBill",
    packageName = "reconciliation.bill",
    description = "Read the authoritative bill and its complete immutable revision history",
    aggregates = ["AuthoritativeBill"],
    family = "query",
)
object GetAuthoritativeBillQry {
    data class Request(val billId: AuthoritativeBillId) : Query<Response>

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
        val revisions: List<Revision>,
    ) {
        data class Revision(
            val revisionId: String,
            val revision: String,
            val publishedAt: Instant,
            val completeness: String,
            val rawEvidence: String,
            val payloadFingerprint: String,
            val records: List<Record>,
        )

        data class Record(
            val recordId: String,
            val recordIdentity: String,
            val transactionKind: String,
            val externalTransactionIdentity: String,
            val amount: BigDecimal,
            val currency: String,
            val rawStatus: String,
            val occurredAt: Instant?,
            val receivedAt: Instant,
            val rawEvidence: String,
        )
    }
}
