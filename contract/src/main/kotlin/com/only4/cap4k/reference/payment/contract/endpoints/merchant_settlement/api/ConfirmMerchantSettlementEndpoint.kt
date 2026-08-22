package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/merchant-settlements/{settlementId}/confirmations
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmMerchantSettlementEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/merchant-settlements/{settlementId}/confirmations",
    aggregates = [],
    operationName = "merchant-settlement.confirm",
    family = "endpoint"
)
object ConfirmMerchantSettlementEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.confirm"

    data class Request(
        val settlementId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val confirmedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        val settlementId: String,
        val status: String,
        val netAmount: BigDecimal
    )

}
