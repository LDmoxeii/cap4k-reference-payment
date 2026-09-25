package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.ConfirmRefundResultEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.CreateRefundAttemptEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.GetRefundEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.ListRefundsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.RequestRefundEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.SubmitRefundAttemptEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class RefundEndpointHttpConfiguration {
    @Bean
    fun requestRefundHttpBinding(): EndpointMvcBinding<RequestRefundEndpoint.Request, RequestRefundEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = RequestRefundEndpoint.OPERATION_NAME,
            requestType = RequestRefundEndpoint.Request::class,
            responseType = RequestRefundEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/refunds",
            requestMapper = EndpointMvcRequestMapper { request -> request.body(RequestRefundEndpoint.Request::class) },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 201),
        )

    @Bean
    fun createRefundAttemptHttpBinding(): EndpointMvcBinding<CreateRefundAttemptEndpoint.Request, CreateRefundAttemptEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = CreateRefundAttemptEndpoint.OPERATION_NAME,
            requestType = CreateRefundAttemptEndpoint.Request::class,
            responseType = CreateRefundAttemptEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/refunds/{refundId}/attempts",
            requestMapper = EndpointMvcRequestMapper { request ->
                val body = request.body(IdempotencyKeyBody::class)
                CreateRefundAttemptEndpoint.Request(
                    refundId = request.path("refundId", String::class),
                    idempotencyKey = body.idempotencyKey,
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 201),
        )

    @Bean
    fun submitRefundAttemptHttpBinding(): EndpointMvcBinding<SubmitRefundAttemptEndpoint.Request, SubmitRefundAttemptEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = SubmitRefundAttemptEndpoint.OPERATION_NAME,
            requestType = SubmitRefundAttemptEndpoint.Request::class,
            responseType = SubmitRefundAttemptEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/refunds/{refundId}/attempts/{refundAttemptId}/submissions",
            requestMapper = EndpointMvcRequestMapper { request ->
                val body = request.body(IdempotencyKeyBody::class)
                SubmitRefundAttemptEndpoint.Request(
                    refundId = request.path("refundId", String::class),
                    refundAttemptId = request.path("refundAttemptId", String::class),
                    idempotencyKey = body.idempotencyKey,
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun confirmRefundResultHttpBinding(): EndpointMvcBinding<ConfirmRefundResultEndpoint.Request, ConfirmRefundResultEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ConfirmRefundResultEndpoint.OPERATION_NAME,
            requestType = ConfirmRefundResultEndpoint.Request::class,
            responseType = ConfirmRefundResultEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/channel/refund-results",
        )

    @Bean
    fun getRefundHttpBinding(): EndpointMvcBinding<GetRefundEndpoint.Request, GetRefundEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetRefundEndpoint.OPERATION_NAME,
            requestType = GetRefundEndpoint.Request::class,
            responseType = GetRefundEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/refunds/{refundId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetRefundEndpoint.Request(refundId = request.path("refundId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun listRefundsHttpBinding(): EndpointMvcBinding<ListRefundsEndpoint.Request, ListRefundsEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ListRefundsEndpoint.OPERATION_NAME,
            requestType = ListRefundsEndpoint.Request::class,
            responseType = ListRefundsEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/refunds/search",
        )
}

private data class IdempotencyKeyBody(
    val idempotencyKey: String,
)
