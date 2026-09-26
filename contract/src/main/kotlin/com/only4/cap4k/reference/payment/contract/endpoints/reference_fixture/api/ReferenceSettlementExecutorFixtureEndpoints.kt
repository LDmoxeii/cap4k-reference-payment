package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ConfigureReferenceSettlementExecutorEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/settlement-executor-script",
    aggregates = [],
    operationName = "reference-fixture.settlement-executor.configure",
    family = "endpoint",
)
object ConfigureReferenceSettlementExecutorEndpoint {
    const val OPERATION_NAME = "reference-fixture.settlement-executor.configure"
    data class Request(val executionId: String?, val script: String?) : EndpointRequest<Response>
    data class Response(
        val executionId: String,
        val script: String,
        val configured: Boolean,
        val consumed: Boolean,
        val observation: String?,
        val diagnosticSummary: String?,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "ResetReferenceSettlementExecutorEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/settlement-executor-script/reset",
    aggregates = [],
    operationName = "reference-fixture.settlement-executor.reset",
    family = "endpoint",
)
object ResetReferenceSettlementExecutorEndpoint {
    const val OPERATION_NAME = "reference-fixture.settlement-executor.reset"
    data class Request(val executionId: String?) : EndpointRequest<Response>
    data class Response(
        val executionId: String,
        val script: String,
        val configured: Boolean,
        val consumed: Boolean,
        val observation: String?,
        val diagnosticSummary: String?,
    )
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetReferenceSettlementExecutorEndpoint",
    packageName = "reference_fixture.api",
    description = "GET /api/reference-fixtures/settlement-executor-script/{executionId}",
    aggregates = [],
    operationName = "reference-fixture.settlement-executor.get",
    family = "endpoint",
)
object GetReferenceSettlementExecutorEndpoint {
    const val OPERATION_NAME = "reference-fixture.settlement-executor.get"
    data class Request(val executionId: String) : EndpointRequest<Response>
    data class Response(
        val executionId: String,
        val script: String,
        val configured: Boolean,
        val consumed: Boolean,
        val observation: String?,
        val diagnosticSummary: String?,
    )
}
