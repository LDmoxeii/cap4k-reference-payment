package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
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
        /**
         * 结算标识
         */
        val settlementId: String = "",
        val idempotencyKey: String,
        val reason: String,
        val evidence: String,
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 结算标识
         */
        val settlementId: String,
        /**
         * 状态
         */
        val status: String,
        /**
         * 净金额
         */
        val netMoney: Money,
        val actorId: String,
        val confirmedAt: Instant,
        val reason: String,
        val evidence: String,
        val receipt: OperationReceipt,
    )

}
