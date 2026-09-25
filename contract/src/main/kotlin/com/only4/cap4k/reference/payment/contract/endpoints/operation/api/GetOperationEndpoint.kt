package com.only4.cap4k.reference.payment.contract.endpoints.operation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.OperationView

/** GET /api/operations/{operationId}. */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "GetOperationEndpoint",
    packageName = "operation.api",
    description = "GET /api/operations/{operationId}",
    aggregates = [],
    operationName = "operation.get",
    family = "endpoint",
)
object GetOperationEndpoint {
    const val OPERATION_NAME: String = "operation.get"

    data class Request(val operationId: String) : EndpointRequest<Response>

    data class Response(val operation: OperationView)
}
