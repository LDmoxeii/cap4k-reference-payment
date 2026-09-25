package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
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
        /**
         * 结算标识
         */
        val settlementId: String = "",
        /**
         * 原因
         */
        val reason: String,
        val evidence: String,
        val idempotencyKey: String,
        /**
         * 是否创建替代项
         */
        val createReplacement: Boolean
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
         * 替代结算标识
         */
        val replacementSettlementId: String?,
        val actorId: String,
        val voidedAt: Instant,
        val reason: String,
        val evidence: String,
        val receipt: OperationReceipt,
    )

}
