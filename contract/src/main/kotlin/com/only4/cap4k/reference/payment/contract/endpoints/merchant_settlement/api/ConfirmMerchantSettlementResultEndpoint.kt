package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant

/**
 * POST /api/channel/settlement-results
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfirmMerchantSettlementResultEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/channel/settlement-results",
    aggregates = [],
    operationName = "merchant-settlement.result.confirm",
    family = "endpoint"
)
object ConfirmMerchantSettlementResultEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.result.confirm"

    data class Request(
        val channelId: String,
        val notificationId: String,
        val settlementId: String,
        val executionAttemptId: String,
        val executionGroupIdentity: String,
        val requestIdentity: String,
        val externalSettlementIdentity: String,
        val amount: BigDecimal,
        val currency: String,
        val result: String,
        val resultCode: String?,
        val occurredAt: Instant,
        val receivedAt: Instant,
        val verificationMaterial: String
    ) : EndpointRequest<Response>

    data class Response(
        val settlementStatus: String,
        val attemptStatus: String?,
        val notificationReceiveCount: Int,
        val disposition: String,
        val rejectionSummary: String?,
        val conflictSummary: String?,
        val reviewSummary: String?,
        val settledFactFormedNow: Boolean
    )

}
