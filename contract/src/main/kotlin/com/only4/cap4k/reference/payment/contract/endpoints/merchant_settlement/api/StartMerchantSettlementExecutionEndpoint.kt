package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/**
 * POST /api/merchant-settlements/{settlementId}/executions
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "StartMerchantSettlementExecutionEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/merchant-settlements/{settlementId}/executions",
    aggregates = [],
    operationName = "merchant-settlement.execution.start",
    family = "endpoint"
)
object StartMerchantSettlementExecutionEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.execution.start"

    data class Request(
        val settlementId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val requestedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        val settlementId: String,
        val attemptId: String?,
        val executionGroupIdentity: String?,
        val requestIdentity: String?,
        val status: String,
        val providerAccepted: Boolean,
        val diagnosticSummary: String?
    )

}
