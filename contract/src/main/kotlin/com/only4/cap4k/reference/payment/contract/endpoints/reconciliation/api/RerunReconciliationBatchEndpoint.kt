package com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/**
 * POST /api/reconciliation-batches/{batchId}/reruns
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "RerunReconciliationBatchEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-batches/{batchId}/reruns",
    aggregates = [],
    operationName = "reconciliation.batch.rerun",
    family = "endpoint"
)
object RerunReconciliationBatchEndpoint {
    const val OPERATION_NAME: String = "reconciliation.batch.rerun"

    data class Request(
        val batchId: String,
        val requestedBy: String,
        val requestedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        val batchId: String,
        val runId: String?,
        val status: String,
        val idempotentReplay: Boolean,
        val statementIdentity: String?,
        val statementRevision: String?
    )

}
