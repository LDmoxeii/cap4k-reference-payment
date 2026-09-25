package com.only4.cap4k.reference.payment.adapter.endpoints.operation

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.operation.api.GetOperationEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class OperationEndpointHttpConfiguration {
    @Bean
    fun getOperationHttpBinding(): EndpointMvcBinding<GetOperationEndpoint.Request, GetOperationEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetOperationEndpoint.OPERATION_NAME,
            requestType = GetOperationEndpoint.Request::class,
            responseType = GetOperationEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/operations/{operationId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetOperationEndpoint.Request(request.path("operationId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )
}
