package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfigureReferencePaymentChannelEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/payment-channel-script",
    aggregates = [],
    operationName = "reference-fixture.payment-channel.configure",
    family = "endpoint",
)
object ConfigureReferencePaymentChannelEndpoint {
    const val OPERATION_NAME = "reference-fixture.payment-channel.configure"
    data class Request(val channelId: String?, val script: String?) : EndpointRequest<Response>
    data class Response(val channelId: String, val script: String)
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ResetReferencePaymentChannelEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/payment-channel-script/reset",
    aggregates = [],
    operationName = "reference-fixture.payment-channel.reset",
    family = "endpoint",
)
object ResetReferencePaymentChannelEndpoint {
    const val OPERATION_NAME = "reference-fixture.payment-channel.reset"
    data class Request(val channelId: String?) : EndpointRequest<Response>
    data class Response(val channelId: String, val script: String)
}
