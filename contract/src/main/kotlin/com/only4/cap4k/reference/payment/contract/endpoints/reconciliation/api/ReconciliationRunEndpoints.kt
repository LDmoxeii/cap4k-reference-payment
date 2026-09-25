package com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import java.time.Instant
import java.time.LocalDate

/** The only public execution resource for reconciliation. Internal batches are not an HTTP resource. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetReconciliationRunEndpoint",
    packageName = "reconciliation.api",
    description = "GET /api/reconciliation-runs/{runId}",
    aggregates = [],
    operationName = "reconciliation.run.get",
    family = "endpoint",
)
object GetReconciliationRunEndpoint {
    const val OPERATION_NAME = "reconciliation.run.get"

    data class Request(val runId: String) : EndpointRequest<Response>

    data class Response(
        val runId: String,
        val channelId: String,
        val currency: String,
        val businessDate: LocalDate,
        val businessTimezone: String,
        val billIdentity: String,
        val billRevision: String,
        val statementCompleteness: String,
        val status: String,
        val finality: Finality,
        val effectiveRun: Boolean,
        val sortTime: Instant,
        val fetchedAt: Instant,
        val startedAt: Instant,
        val completedAt: Instant?,
        val matchedCount: Int,
        val differenceCount: Int,
        val unresolvedDifferenceCount: Int,
        val settlementBlocked: Boolean,
        val blockingReason: String?,
        val failureSummary: String?,
        val differences: List<GetReconciliationBatchEndpoint.Response.ReconciliationItemSummary>,
    )
}

/**
 * A JSON search request is intentional: the CAP4K special HTTP mapper exposes required query
 * values only, while all list filters are optional.  It still represents one authoritative,
 * cursor-paged collection and never loads aggregate collections in the controller.
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ListReconciliationRunsEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-runs/search",
    aggregates = [],
    operationName = "reconciliation.run.list",
    family = "endpoint",
)
object ListReconciliationRunsEndpoint {
    const val OPERATION_NAME = "reconciliation.run.list"

    data class Request(
        val merchantId: String,
        val status: String? = null,
        val finality: Finality? = null,
        val runId: String? = null,
        val channelId: String? = null,
        val billId: String? = null,
        val billRevision: String? = null,
        val businessDate: LocalDate? = null,
        val currency: String? = null,
        val effectiveRun: Boolean? = null,
        val createdFrom: Instant? = null,
        val createdTo: Instant? = null,
        val cursor: String? = null,
        val pageSize: Int? = null,
    ) : EndpointRequest<Response>

    data class Response(
        val items: List<Item>,
        val nextCursor: String?,
        val pageSize: Int,
    ) {
        data class Item(
            val runId: String,
            val channelId: String,
            val currency: String,
            val businessDate: LocalDate,
            val billId: String,
            val billRevision: String,
            val status: String,
            val finality: Finality,
            val effectiveRun: Boolean,
            val sortTime: Instant,
            val completedAt: Instant?,
            val matchedCount: Int,
            val differenceCount: Int,
            val unresolvedDifferenceCount: Int,
            val settlementBlocked: Boolean,
        )
    }
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "RerunReconciliationRunEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-runs/{runId}/reruns",
    aggregates = [],
    operationName = "reconciliation.run.rerun",
    family = "endpoint",
)
object RerunReconciliationRunEndpoint {
    const val OPERATION_NAME = "reconciliation.run.rerun"
    data class Request(val runId: String = "", val idempotencyKey: String) : EndpointRequest<Response>
    data class Response(
        val runId: String?,
        val status: String,
        val finality: Finality,
        val idempotentReplay: Boolean,
        val billIdentity: String?,
        val billRevision: String?,
        val receipt: OperationReceipt,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "DisposeReconciliationRunDifferenceEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-runs/{runId}/differences/{itemId}/dispositions",
    aggregates = [],
    operationName = "reconciliation.run.difference.dispose",
    family = "endpoint",
)
object DisposeReconciliationRunDifferenceEndpoint {
    const val OPERATION_NAME = "reconciliation.run.difference.dispose"
    data class Request(
        val runId: String = "",
        val itemId: String = "",
        val merchantId: String,
        val channelId: String? = null,
        val conclusion: String,
        val settlementImpact: String,
        val reason: String,
        val evidence: String,
        val followUp: String? = null,
        val idempotencyKey: String,
    ) : EndpointRequest<Response>

    data class Response(
        val dispositionId: String,
        val actorId: String,
        val reason: String,
        val evidence: String,
        val decidedAt: Instant,
        val status: String,
        val authorization: String,
        val confirmationFactId: String?,
        val settlementBlocked: Boolean,
        val blockingReason: String?,
        val receipt: OperationReceipt,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmReconciliationFactEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-runs/{runId}/differences/{itemId}/confirmations",
    aggregates = [],
    operationName = "reconciliation.run.fact-confirm",
    family = "endpoint",
)
object ConfirmReconciliationFactEndpoint {
    const val OPERATION_NAME = "reconciliation.run.fact-confirm"
    data class Request(
        val runId: String = "",
        val itemId: String = "",
        val merchantId: String? = null,
        val channelId: String? = null,
        val reason: String,
        val evidence: String,
        val idempotencyKey: String,
    ) : EndpointRequest<Response>

    data class Response(
        val confirmationFactId: String,
        val actorId: String,
        val reason: String,
        val evidence: String,
        val recordedAt: Instant,
        val dispositionId: String,
        val settlementBlocked: Boolean,
        val blockingReason: String?,
        val receipt: OperationReceipt,
    )
}
