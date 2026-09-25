package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.AdjudicatePaymentReviewEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.ConfirmPaymentResultEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.CreatePaymentAttemptEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.CreatePaymentEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentChannelResultReceiptsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentTimelineEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.ListPaymentIntentsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.SubmitPaymentAttemptEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

@Configuration(proxyBeanMethods = false)
class PaymentEndpointHttpConfiguration(
    private val actorContextResolver: ReferenceActorContextResolver,
) {

    @Bean
    fun paymentClock(): Clock = Clock.systemUTC()

    @Bean
    fun createPaymentHttpBinding(): EndpointMvcBinding<CreatePaymentEndpoint.Request, CreatePaymentEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = CreatePaymentEndpoint.OPERATION_NAME,
            requestType = CreatePaymentEndpoint.Request::class,
            responseType = CreatePaymentEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/payments",
            requestMapper = EndpointMvcRequestMapper { request -> request.body(CreatePaymentEndpoint.Request::class) },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 201),
        )

    @Bean
    fun createPaymentAttemptHttpBinding(): EndpointMvcBinding<CreatePaymentAttemptEndpoint.Request, CreatePaymentAttemptEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = CreatePaymentAttemptEndpoint.OPERATION_NAME,
            requestType = CreatePaymentAttemptEndpoint.Request::class,
            responseType = CreatePaymentAttemptEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/payments/{paymentId}/attempts",
            requestMapper = EndpointMvcRequestMapper { request ->
                val body = request.body(CreatePaymentAttemptBody::class)
                CreatePaymentAttemptEndpoint.Request(
                    paymentId = request.path("paymentId", String::class),
                    idempotencyKey = body.idempotencyKey,
                    riskReason = body.riskReason,
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 201),
        )

    @Bean
    fun submitPaymentAttemptHttpBinding(): EndpointMvcBinding<SubmitPaymentAttemptEndpoint.Request, SubmitPaymentAttemptEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = SubmitPaymentAttemptEndpoint.OPERATION_NAME,
            requestType = SubmitPaymentAttemptEndpoint.Request::class,
            responseType = SubmitPaymentAttemptEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/payments/{paymentId}/attempts/{paymentAttemptId}/submissions",
            requestMapper = EndpointMvcRequestMapper { request ->
                val body = request.body(IdempotencyKeyBody::class)
                SubmitPaymentAttemptEndpoint.Request(
                    paymentId = request.path("paymentId", String::class),
                    paymentAttemptId = request.path("paymentAttemptId", String::class),
                    idempotencyKey = body.idempotencyKey,
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun confirmPaymentResultHttpBinding(): EndpointMvcBinding<ConfirmPaymentResultEndpoint.Request, ConfirmPaymentResultEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ConfirmPaymentResultEndpoint.OPERATION_NAME,
            requestType = ConfirmPaymentResultEndpoint.Request::class,
            responseType = ConfirmPaymentResultEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/channel/payment-results",
        )

    @Bean
    fun adjudicatePaymentReviewHttpBinding(): EndpointMvcBinding<AdjudicatePaymentReviewEndpoint.Request, AdjudicatePaymentReviewEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = AdjudicatePaymentReviewEndpoint.OPERATION_NAME,
            requestType = AdjudicatePaymentReviewEndpoint.Request::class,
            responseType = AdjudicatePaymentReviewEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/payments/{paymentId}/reviews/{reviewId}/decisions",
            requestMapper = EndpointMvcRequestMapper { request ->
                val body = request.body(AdjudicatePaymentReviewBody::class)
                actorContextResolver.bind(
                    runCatching { request.header(ReferenceActorContextResolver.HEADER_NAME) }.getOrNull(),
                )
                AdjudicatePaymentReviewEndpoint.Request(
                    paymentId = request.path("paymentId", String::class),
                    merchantId = body.merchantId,
                    idempotencyKey = body.idempotencyKey,
                    reviewId = request.path("reviewId", String::class),
                    decisionIdentity = body.decisionIdentity,
                    decision = body.decision,
                    reason = body.reason,
                    evidence = body.evidence,
                    eligibilityImpact = body.eligibilityImpact,
                    remediationReference = body.remediationReference,
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun getPaymentHttpBinding(): EndpointMvcBinding<GetPaymentEndpoint.Request, GetPaymentEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetPaymentEndpoint.OPERATION_NAME,
            requestType = GetPaymentEndpoint.Request::class,
            responseType = GetPaymentEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/payments/{paymentId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetPaymentEndpoint.Request(paymentId = request.path("paymentId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun getPaymentChannelResultReceiptsHttpBinding(): EndpointMvcBinding<GetPaymentChannelResultReceiptsEndpoint.Request, GetPaymentChannelResultReceiptsEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetPaymentChannelResultReceiptsEndpoint.OPERATION_NAME,
            requestType = GetPaymentChannelResultReceiptsEndpoint.Request::class,
            responseType = GetPaymentChannelResultReceiptsEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/channel/payment-results/{resultIdentity}/receipts",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetPaymentChannelResultReceiptsEndpoint.Request(
                    resultIdentity = request.path("resultIdentity", String::class),
                    channelId = runCatching { request.query("channelId", String::class) }.getOrNull(),
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun getPaymentTimelineHttpBinding(): EndpointMvcBinding<GetPaymentTimelineEndpoint.Request, GetPaymentTimelineEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetPaymentTimelineEndpoint.OPERATION_NAME,
            requestType = GetPaymentTimelineEndpoint.Request::class,
            responseType = GetPaymentTimelineEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/payments/{paymentId}/timeline",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetPaymentTimelineEndpoint.Request(
                    paymentId = request.path("paymentId", String::class),
                    pageSize = runCatching { request.query("pageSize", Int::class) }.getOrNull(),
                    cursor = runCatching { request.query("cursor", String::class) }.getOrNull(),
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun listPaymentIntentsHttpBinding(): EndpointMvcBinding<ListPaymentIntentsEndpoint.Request, ListPaymentIntentsEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ListPaymentIntentsEndpoint.OPERATION_NAME,
            requestType = ListPaymentIntentsEndpoint.Request::class,
            responseType = ListPaymentIntentsEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/payments/search",
        )
}

data class IdempotencyKeyBody(
    val idempotencyKey: String,
)

data class CreatePaymentAttemptBody(
    val idempotencyKey: String,
    val riskReason: String? = null,
)

data class AdjudicatePaymentReviewBody(
    val merchantId: String,
    val idempotencyKey: String,
    val decisionIdentity: String,
    val decision: String,
    val reason: String,
    val evidence: String,
    val eligibilityImpact: String,
    val remediationReference: String? = null,
)

/**
 * Payment Money is immutable after intent creation.  Registering the route explicitly makes that
 * business prohibition observable as a stable 405/ApiError instead of looking like a missing
 * resource when Spring has no mutable endpoint to dispatch.
 */
@RestController
class PaymentImmutableMutationHttpGuard {
    @PutMapping("/api/payments/{paymentId}")
    fun rejectPaymentMutation(): Nothing =
        throw PaymentImmutableMutationException()
}

class PaymentImmutableMutationException : RuntimeException("支付创建后不允许修改金额或币种")
