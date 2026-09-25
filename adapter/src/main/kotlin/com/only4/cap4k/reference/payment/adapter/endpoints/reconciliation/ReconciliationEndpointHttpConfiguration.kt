package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.GetReconciliationRunEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.ListReconciliationRunsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.RerunReconciliationRunEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.DisposeReconciliationRunDifferenceEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.ConfirmReconciliationFactEndpoint
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class ReconciliationEndpointHttpConfiguration(
    private val actorContextResolver: ReferenceActorContextResolver,
) {
    @Bean
    fun getReconciliationRunHttpBinding(): EndpointMvcBinding<GetReconciliationRunEndpoint.Request, GetReconciliationRunEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetReconciliationRunEndpoint.OPERATION_NAME,
            requestType = GetReconciliationRunEndpoint.Request::class,
            responseType = GetReconciliationRunEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/reconciliation-runs/{runId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetReconciliationRunEndpoint.Request(request.path("runId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun listReconciliationRunsHttpBinding(): EndpointMvcBinding<ListReconciliationRunsEndpoint.Request, ListReconciliationRunsEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ListReconciliationRunsEndpoint.OPERATION_NAME,
            requestType = ListReconciliationRunsEndpoint.Request::class,
            responseType = ListReconciliationRunsEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reconciliation-runs/search",
        )

    @Bean
    fun rerunReconciliationRunHttpBinding(): EndpointMvcBinding<RerunReconciliationRunEndpoint.Request, RerunReconciliationRunEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = RerunReconciliationRunEndpoint.OPERATION_NAME,
            requestType = RerunReconciliationRunEndpoint.Request::class,
            responseType = RerunReconciliationRunEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reconciliation-runs/{runId}/reruns",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(RerunReconciliationRunEndpoint.Request::class).copy(
                    runId = request.path("runId", String::class),
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun disposeReconciliationRunDifferenceHttpBinding(): EndpointMvcBinding<DisposeReconciliationRunDifferenceEndpoint.Request, DisposeReconciliationRunDifferenceEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = DisposeReconciliationRunDifferenceEndpoint.OPERATION_NAME,
            requestType = DisposeReconciliationRunDifferenceEndpoint.Request::class,
            responseType = DisposeReconciliationRunDifferenceEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reconciliation-runs/{runId}/differences/{itemId}/dispositions",
            requestMapper = EndpointMvcRequestMapper { request ->
                val body = request.body(DisposeReconciliationRunDifferenceEndpoint.Request::class)
                actorContextResolver.bind(
                    runCatching { request.header(ReferenceActorContextResolver.HEADER_NAME) }.getOrNull(),
                )
                body.copy(
                    runId = request.path("runId", String::class),
                    itemId = request.path("itemId", String::class),
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun confirmReconciliationFactHttpBinding(): EndpointMvcBinding<ConfirmReconciliationFactEndpoint.Request, ConfirmReconciliationFactEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = ConfirmReconciliationFactEndpoint.OPERATION_NAME,
            requestType = ConfirmReconciliationFactEndpoint.Request::class,
            responseType = ConfirmReconciliationFactEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reconciliation-runs/{runId}/differences/{itemId}/confirmations",
            requestMapper = EndpointMvcRequestMapper { request ->
                val body = request.body(ConfirmReconciliationFactEndpoint.Request::class)
                actorContextResolver.bind(
                    runCatching { request.header(ReferenceActorContextResolver.HEADER_NAME) }.getOrNull(),
                )
                body.copy(
                    runId = request.path("runId", String::class),
                    itemId = request.path("itemId", String::class),
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

}
