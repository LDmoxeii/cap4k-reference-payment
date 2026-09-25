package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.reference_fixture.operation.CreateReferenceOperationFixtureCmd
import com.only4.cap4k.reference.payment.application.commands.reference_fixture.operation.TransitionReferenceOperationFixtureCmd
import com.only4.cap4k.reference.payment.application.queries.operation.GetReferenceOperationFixtureResourceQry
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.CreateReferenceOperationFixtureEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferenceOperationFixtureResourceEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.TransitionReferenceOperationFixtureEndpoint
import org.springframework.stereotype.Component

@Component
class CreateReferenceOperationFixtureEndpointHandler : EndpointHandler<
    CreateReferenceOperationFixtureEndpoint.Request,
    CreateReferenceOperationFixtureEndpoint.Response,
> {
    override fun handle(request: CreateReferenceOperationFixtureEndpoint.Request): CreateReferenceOperationFixtureEndpoint.Response {
        val response = Mediator.commands.send(
            CreateReferenceOperationFixtureCmd.Request(
                request.merchantId,
                request.commandType,
                request.idempotencyKey,
                request.resourceType,
                request.resourceId,
                request.readAfterMode,
                request.resourceUrl,
            ),
        )
        return CreateReferenceOperationFixtureEndpoint.Response(response.receipt)
    }
}

@Component
class TransitionReferenceOperationFixtureEndpointHandler : EndpointHandler<
    TransitionReferenceOperationFixtureEndpoint.Request,
    TransitionReferenceOperationFixtureEndpoint.Response,
> {
    override fun handle(request: TransitionReferenceOperationFixtureEndpoint.Request): TransitionReferenceOperationFixtureEndpoint.Response {
        val response = Mediator.commands.send(
            TransitionReferenceOperationFixtureCmd.Request(
                request.operationId,
                request.outcome,
                request.resourceUrl,
                request.result,
                request.errorCode,
                request.errorMessage,
                request.errorDetails,
                request.retryable,
                request.reviewId,
            ),
        )
        return TransitionReferenceOperationFixtureEndpoint.Response(response.operation)
    }
}

@Component
class GetReferenceOperationFixtureResourceEndpointHandler : EndpointHandler<
    GetReferenceOperationFixtureResourceEndpoint.Request,
    GetReferenceOperationFixtureResourceEndpoint.Response,
> {
    override fun handle(request: GetReferenceOperationFixtureResourceEndpoint.Request) =
        GetReferenceOperationFixtureResourceEndpoint.Response(
            Mediator.queries.ask(GetReferenceOperationFixtureResourceQry.Request(request.resourceId)).operation,
        )
}
