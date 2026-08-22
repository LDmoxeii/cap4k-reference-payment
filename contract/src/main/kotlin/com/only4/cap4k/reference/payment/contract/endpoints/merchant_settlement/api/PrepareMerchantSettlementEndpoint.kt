package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * POST /api/merchant-settlements
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "PrepareMerchantSettlementEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/merchant-settlements",
    aggregates = [],
    operationName = "merchant-settlement.prepare",
    family = "endpoint"
)
object PrepareMerchantSettlementEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.prepare"

    data class Request(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val settlementDate: LocalDate,
        val requestedBy: String,
        val requestedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        val settlementId: String?,
        val status: String?,
        val created: Boolean,
        val idempotentReplay: Boolean,
        val noOp: Boolean,
        val eligibleCount: Int,
        val excludedCount: Int,
        val blockerSummary: String?,
        val paymentGrossAmount: BigDecimal,
        val refundGrossAmount: BigDecimal,
        val feeTotalAmount: BigDecimal,
        val adjustmentTotalAmount: BigDecimal,
        val netAmount: BigDecimal
    )

}
