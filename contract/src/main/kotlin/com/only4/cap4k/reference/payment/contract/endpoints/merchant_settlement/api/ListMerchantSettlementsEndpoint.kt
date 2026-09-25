package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.Money
import java.time.Instant

/** Authoritative Settlement collection; its scope remains merchant + currency + settlementPeriod. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ListMerchantSettlementsEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/merchant-settlements/search",
    aggregates = [],
    operationName = "merchant-settlement.list",
    family = "endpoint",
)
object ListMerchantSettlementsEndpoint {
    const val OPERATION_NAME = "merchant-settlement.list"

    data class Request(
        val merchantId: String? = null,
        /** Unified public status, e.g. PREPARING, READY_FOR_CONFIRMATION, CONFIRMED, EXECUTING, SETTLED. */
        val status: String? = null,
        val finality: Finality? = null,
        val settlementId: String? = null,
        val currency: String? = null,
        val periodStart: Instant? = null,
        val periodEnd: Instant? = null,
        val businessTimezone: String? = null,
        /** Unified execution status: SUBMITTED, SUCCESS, FAILURE, or UNKNOWN. */
        val executionStatus: String? = null,
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
            val settlementId: String,
            val merchantId: String,
            val currency: String,
            val periodStart: Instant,
            val periodEnd: Instant,
            val businessTimezone: String,
            val status: String,
            val finality: Finality,
            val sortTime: Instant,
            val grossMoney: Money,
            val refundMoney: Money,
            val feeMoney: Money,
            val adjustmentMoney: Money,
            val netMoney: Money,
            val compositionFrozen: Boolean,
            val executionStatus: String?,
            val predecessorSettlementId: String?,
            val replacementSettlementId: String?,
        )
    }
}
