package com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/**
 * POST /api/reconciliation-items/{itemId}/dispositions
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "DisposeReconciliationDifferenceEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-items/{itemId}/dispositions",
    aggregates = [],
    operationName = "reconciliation.difference.dispose",
    family = "endpoint"
)
object DisposeReconciliationDifferenceEndpoint {
    const val OPERATION_NAME: String = "reconciliation.difference.dispose"

    data class Request(
        val batchId: String,
        val itemId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val conclusion: String,
        val settlementImpact: String,
        val evidence: String,
        val followUp: String?,
        val disposedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        val dispositionId: String,
        val authorization: String,
        val status: String,
        val confirmationFactId: String?,
        val batchStatus: String,
        val settlementBlocked: Boolean,
        val blockingReason: String?
    )

}
