package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.contract.common.OperationView

/**
 * Reference-only Operation controls used by the shared black-box acceptance runner. They exercise
 * the real Operation aggregate and Unit of Work, but do not represent a production administration
 * API and never mutate a payment, refund, reconciliation run, or settlement.
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreateReferenceOperationFixtureEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/operations",
    aggregates = ["Operation"],
    operationName = "reference-fixture.operation.create",
    family = "endpoint",
)
object CreateReferenceOperationFixtureEndpoint {
    const val OPERATION_NAME = "reference-fixture.operation.create"

    data class Request(
        val merchantId: String,
        val commandType: String,
        val idempotencyKey: String,
        val resourceType: String = "REFERENCE_FIXTURE",
        val resourceId: String,
        val readAfterMode: String,
        val resourceUrl: String? = null,
    ) : EndpointRequest<Response>

    data class Response(val receipt: OperationReceipt)
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "TransitionReferenceOperationFixtureEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/operations/{operationId}/transitions",
    aggregates = ["Operation"],
    operationName = "reference-fixture.operation.transition",
    family = "endpoint",
)
object TransitionReferenceOperationFixtureEndpoint {
    const val OPERATION_NAME = "reference-fixture.operation.transition"

    data class Request(
        val operationId: String = "",
        val outcome: String,
        val resourceUrl: String? = null,
        val result: Map<String, Any?>? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val errorDetails: Map<String, Any?>? = null,
        val retryable: Boolean? = null,
        val reviewId: String? = null,
    ) : EndpointRequest<Response>

    data class Response(val operation: OperationView)
}

@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetReferenceOperationFixtureResourceEndpoint",
    packageName = "reference_fixture.api",
    description = "GET /api/reference-fixtures/operation-resources/{resourceId}",
    aggregates = ["Operation"],
    operationName = "reference-fixture.operation-resource.get",
    family = "endpoint",
)
object GetReferenceOperationFixtureResourceEndpoint {
    const val OPERATION_NAME = "reference-fixture.operation-resource.get"

    data class Request(val resourceId: String) : EndpointRequest<Response>

    data class Response(val operation: OperationView)
}
