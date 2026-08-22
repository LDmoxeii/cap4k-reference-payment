package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/**
 * POST /api/merchant-settlements/{settlementId}/voids
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "VoidMerchantSettlementEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/merchant-settlements/{settlementId}/voids",
    aggregates = [],
    operationName = "merchant-settlement.void",
    family = "endpoint"
)
object VoidMerchantSettlementEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.void"

    data class Request(
        val settlementId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val reason: String,
        val voidedAt: Instant,
        val createReplacement: Boolean
    ) : EndpointRequest<Response>

    data class Response(
        val settlementId: String,
        val status: String,
        val replacementSettlementId: String?
    )

}
