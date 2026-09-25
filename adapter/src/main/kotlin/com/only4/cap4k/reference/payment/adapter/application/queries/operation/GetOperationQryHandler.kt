package com.only4.cap4k.reference.payment.adapter.application.queries.operation

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.queries.operation.GetOperationQry
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "GetOperation",
    packageName = "operation.read",
    description = "Read one persisted operation and its authoritative receipt state",
    aggregates = ["Operation"],
    family = "query-handler",
)
class GetOperationQryHandler(
    private val operationSupport: OperationSupport,
) : QueryHandler<GetOperationQry.Request, GetOperationQry.Response> {
    override fun handle(query: GetOperationQry.Request): GetOperationQry.Response =
        GetOperationQry.Response(operationSupport.get(query.operationId))
}
