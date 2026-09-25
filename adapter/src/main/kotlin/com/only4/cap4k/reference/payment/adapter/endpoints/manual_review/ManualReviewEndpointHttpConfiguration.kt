package com.only4.cap4k.reference.payment.adapter.endpoints.manual_review

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.GetManualReviewEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.ListManualReviewsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.ResolveManualReviewEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class ManualReviewEndpointHttpConfiguration(
    private val actorContextResolver: ReferenceActorContextResolver,
) {
    @Bean
    fun getManualReviewHttpBinding(): EndpointMvcBinding<GetManualReviewEndpoint.Request, GetManualReviewEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetManualReviewEndpoint.OPERATION_NAME,
            requestType = GetManualReviewEndpoint.Request::class,
            responseType = GetManualReviewEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/manual-reviews/{reviewId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetManualReviewEndpoint.Request(request.path("reviewId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun listManualReviewsHttpBinding(): EndpointMvcBinding<ListManualReviewsEndpoint.Request, ListManualReviewsEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ListManualReviewsEndpoint.OPERATION_NAME,
            requestType = ListManualReviewsEndpoint.Request::class,
            responseType = ListManualReviewsEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/manual-reviews/search",
        )

    @Bean
    fun resolveManualReviewHttpBinding(): EndpointMvcBinding<ResolveManualReviewEndpoint.Request, ResolveManualReviewEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = ResolveManualReviewEndpoint.OPERATION_NAME,
            requestType = ResolveManualReviewEndpoint.Request::class,
            responseType = ResolveManualReviewEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/manual-reviews/{reviewId}/resolutions",
            requestMapper = EndpointMvcRequestMapper { request ->
                // Complete all fallible body/path mapping first.  The one-shot ThreadLocal context
                // is then bound immediately before returning, so mapper failures cannot strand it.
                val body = request.body(ResolveManualReviewEndpoint.Request::class)
                val mapped = body.copy(reviewId = request.path("reviewId", String::class))
                actorContextResolver.bind(
                    runCatching { request.header(ReferenceActorContextResolver.HEADER_NAME) }.getOrNull(),
                )
                mapped
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )
}
