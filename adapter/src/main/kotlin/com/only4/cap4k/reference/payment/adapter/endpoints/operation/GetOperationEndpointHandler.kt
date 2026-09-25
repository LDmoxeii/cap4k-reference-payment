package com.only4.cap4k.reference.payment.adapter.endpoints.operation

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.queries.operation.GetOperationQry
import com.only4.cap4k.reference.payment.contract.endpoints.operation.api.GetOperationEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.operation.OperationId
import org.springframework.stereotype.Component

@Component
class GetOperationEndpointHandler : EndpointHandler<GetOperationEndpoint.Request, GetOperationEndpoint.Response> {
    override fun handle(request: GetOperationEndpoint.Request): GetOperationEndpoint.Response =
        GetOperationEndpoint.Response(
            Mediator.queries.ask(GetOperationQry.Request(OperationId.parse(request.operationId))).operation,
        )
}
