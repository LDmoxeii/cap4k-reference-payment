package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal

/** Reference-only merchant routing input. It is not a production merchant administration API. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfigureReferenceMerchantChannelEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/merchant-channels",
    aggregates = ["MerchantChannelConfiguration"],
    operationName = "reference-fixture.merchant-channel.configure",
    family = "endpoint",
)
object ConfigureReferenceMerchantChannelEndpoint {
    const val OPERATION_NAME = "reference-fixture.merchant-channel.configure"

    data class Request(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val paymentMethod: String,
        val status: String,
        val minimumAmount: BigDecimal,
        val maximumAmount: BigDecimal,
        val routingPriority: Int,
        val refundWindowDays: Int,
        val refundResultReviewAfterMinutes: Int,
        val settlementFeeBasisPoints: Int,
        val settlementFixedFeeAmount: BigDecimal,
        val settlementFeeRoundingMode: String,
        val settlementResultReviewAfterMinutes: Int,
    ) : EndpointRequest<Response>

    data class Response(
        val configurationId: String,
        val merchantId: String,
        val channelId: String,
        val status: String,
        val updated: Boolean,
    )
}

/** Executes ordinary time-based commands at the current logical clock instant. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "RunReferenceMaintenanceEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/maintenance",
    aggregates = ["Payment", "Refund", "MerchantSettlement"],
    operationName = "reference-fixture.maintenance.run",
    family = "endpoint",
)
object RunReferenceMaintenanceEndpoint {
    const val OPERATION_NAME = "reference-fixture.maintenance.run"

    data class Request(val action: String) : EndpointRequest<Response>
    data class Response(
        val action: String,
        val inspectedCount: Int,
        val changedCount: Int,
        val reviewOpenedCount: Int,
    )
}
