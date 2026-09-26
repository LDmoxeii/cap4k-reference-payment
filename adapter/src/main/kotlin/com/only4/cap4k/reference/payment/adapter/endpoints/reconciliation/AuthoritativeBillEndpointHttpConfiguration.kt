package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.GetAuthoritativeBillEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.ListBillRevisionsEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class AuthoritativeBillEndpointHttpConfiguration {
    @Bean
    fun getAuthoritativeBillHttpBinding(): EndpointMvcBinding<GetAuthoritativeBillEndpoint.Request, GetAuthoritativeBillEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetAuthoritativeBillEndpoint.OPERATION_NAME,
            requestType = GetAuthoritativeBillEndpoint.Request::class,
            responseType = GetAuthoritativeBillEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/authoritative-bills/{billId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetAuthoritativeBillEndpoint.Request(request.path("billId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun listBillRevisionsHttpBinding(): EndpointMvcBinding<ListBillRevisionsEndpoint.Request, ListBillRevisionsEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = ListBillRevisionsEndpoint.OPERATION_NAME,
            requestType = ListBillRevisionsEndpoint.Request::class,
            responseType = ListBillRevisionsEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/authoritative-bills/{billId}/revisions",
            requestMapper = EndpointMvcRequestMapper { request ->
                ListBillRevisionsEndpoint.Request(request.path("billId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )
}
