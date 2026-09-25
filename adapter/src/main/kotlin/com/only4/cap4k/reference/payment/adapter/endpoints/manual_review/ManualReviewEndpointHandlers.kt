package com.only4.cap4k.reference.payment.adapter.endpoints.manual_review

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.application.queries.manual_review.read.ManualReviewReadModel
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.GetManualReviewEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.ListManualReviewsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.ResolveManualReviewEndpoint
import com.only4.cap4k.reference.payment.application.commands.manual_review.ResolveManualReviewCmd
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import com.only4.cap4k.ddd.core.Mediator
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class GetManualReviewEndpointHandler(
    private val readModel: ManualReviewReadModel,
) : EndpointHandler<GetManualReviewEndpoint.Request, GetManualReviewEndpoint.Response> {
    override fun handle(request: GetManualReviewEndpoint.Request): GetManualReviewEndpoint.Response =
        GetManualReviewEndpoint.Response(readModel.detail(request.reviewId))
}

@Component
class ListManualReviewsEndpointHandler(
    private val readModel: ManualReviewReadModel,
) : EndpointHandler<ListManualReviewsEndpoint.Request, ListManualReviewsEndpoint.Response> {
    override fun handle(request: ListManualReviewsEndpoint.Request): ListManualReviewsEndpoint.Response = readModel.list(request)
}

@Component
class ResolveManualReviewEndpointHandler(
    private val readModel: ManualReviewReadModel,
    private val actorContextResolver: ReferenceActorContextResolver,
    private val clock: Clock,
) : EndpointHandler<ResolveManualReviewEndpoint.Request, ResolveManualReviewEndpoint.Response> {
    override fun handle(request: ResolveManualReviewEndpoint.Request): ResolveManualReviewEndpoint.Response {
        val actor = actorContextResolver.consume()
        val response = Mediator.commands.send(
            ResolveManualReviewCmd.Request(
                reviewId = request.reviewId,
                idempotencyKey = request.idempotencyKey,
                merchantId = request.merchantId,
                outcome = request.outcome,
                reason = request.reason,
                evidence = request.evidence,
                remediationReference = request.remediationReference,
                channelId = request.channelId,
                actorId = actor.actorId,
                actorRole = actor.role,
                resolvedAt = Instant.now(clock),
            ),
        )
        return ResolveManualReviewEndpoint.Response(
            review = readModel.detail(response.reviewId),
            receipt = response.receipt,
        )
    }
}
