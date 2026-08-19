package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.ConfirmRefundResultEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.CreateRefundEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.GetRefundEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class RefundEndpointHttpConfiguration {
    @Bean
    fun createRefundHttpBinding(): EndpointMvcBinding<CreateRefundEndpoint.Request, CreateRefundEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = CreateRefundEndpoint.OPERATION_NAME,
            requestType = CreateRefundEndpoint.Request::class,
            responseType = CreateRefundEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/refunds",
            requestMapper = EndpointMvcRequestMapper { request -> request.body(CreateRefundEndpoint.Request::class) },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 201),
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
}
