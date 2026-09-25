package com.only4.cap4k.reference.payment.application.queries.operation

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.reference.payment.contract.common.OperationView
import com.only4.cap4k.reference.payment.domain.aggregates.operation.OperationId

@DesignBlockMetadata(
    tag = "query",
    name = "GetOperation",
    packageName = "operation.read",
    description = "Read one persisted operation and its authoritative receipt state",
    aggregates = ["Operation"],
    family = "query",
)
object GetOperationQry {
    data class Request(val operationId: OperationId) : Query<Response>
    data class Response(val operation: OperationView)
}
