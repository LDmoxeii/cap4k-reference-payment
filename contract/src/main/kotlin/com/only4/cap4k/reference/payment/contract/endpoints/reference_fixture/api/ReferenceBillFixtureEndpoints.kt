package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant
import java.time.LocalDate

/** Reference provider control: persists immutable bill evidence but never supplies a match verdict. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "RegisterReferenceAuthoritativeBillRevisionEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/bills",
    aggregates = [],
    operationName = "reference-fixture.bill-revision.register",
    family = "endpoint",
)
object RegisterReferenceAuthoritativeBillRevisionEndpoint {
    const val OPERATION_NAME = "reference-fixture.bill-revision.register"

    data class Request(
        val channelId: String?,
        val billIdentity: String?,
        val businessDate: LocalDate?,
        val currency: String?,
        val businessTimezone: String?,
        val revision: String?,
        val completeness: String?,
        val rawEvidence: String?,
        val payloadFingerprint: String?,
        val publishedAt: Instant?,
        val records: List<Record>?,
        /** Reference provider script only; it controls temporary readability, not reconciliation. */
        val unavailableReadCount: Int? = null,
    ) : EndpointRequest<Response> {
        data class Record(
            val recordIdentity: String?,
            val channelTransactionIdentity: String?,
            val transactionKind: String?,
            val money: Money?,
            val rawStatus: String?,
            val occurredAt: Instant?,
            val receivedAt: Instant?,
            val rawEvidence: String?,
        )
    }

    data class Response(
        val billId: String,
        val billIdentity: String,
        val revision: String,
        val currentRevision: String?,
        val idempotentReplay: Boolean,
        val becameCurrent: Boolean,
    )
}

/** Provider signal control. The business path subsequently pulls the registered immutable body. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "SignalReferenceAuthoritativeBillAvailableEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/bills/{billIdentity}/signals",
    aggregates = [],
    operationName = "reference-fixture.bill-available.signal",
    family = "endpoint",
)
object SignalReferenceAuthoritativeBillAvailableEndpoint {
    const val OPERATION_NAME = "reference-fixture.bill-available.signal"

    data class Request(
        /** Bound from the stable path identity by the HTTP adapter; body callers need not repeat it. */
        val billIdentity: String? = null,
        val channelId: String?,
        val businessDate: LocalDate?,
        val currency: String?,
        val businessTimezone: String?,
        val signalIdentity: String?,
        val announcedRevision: String?,
        val publishedAt: Instant?,
        val correlationIdentity: String? = null,
        val causationIdentity: String? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val billId: String,
        val signalId: String,
        val runId: String?,
        val runStatus: String,
        val idempotentReplay: Boolean,
        val diagnostic: String?,
    )
}
