package com.only4.cap4k.reference.payment.adapter.endpoints.reconciliation

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.DisposeReconciliationDifferenceEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.GetReconciliationBatchEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api.RerunReconciliationBatchEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class ReconciliationEndpointHttpConfiguration {
    @Bean
    fun getReconciliationBatchHttpBinding(): EndpointMvcBinding<GetReconciliationBatchEndpoint.Request, GetReconciliationBatchEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetReconciliationBatchEndpoint.OPERATION_NAME,
            requestType = GetReconciliationBatchEndpoint.Request::class,
            responseType = GetReconciliationBatchEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/reconciliation-batches/{batchId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetReconciliationBatchEndpoint.Request(request.path("batchId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun rerunReconciliationBatchHttpBinding(): EndpointMvcBinding<RerunReconciliationBatchEndpoint.Request, RerunReconciliationBatchEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = RerunReconciliationBatchEndpoint.OPERATION_NAME,
            requestType = RerunReconciliationBatchEndpoint.Request::class,
            responseType = RerunReconciliationBatchEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reconciliation-batches/{batchId}/reruns",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(RerunReconciliationBatchEndpoint.Request::class).copy(
                    batchId = request.path("batchId", String::class)
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun disposeReconciliationDifferenceHttpBinding(): EndpointMvcBinding<DisposeReconciliationDifferenceEndpoint.Request, DisposeReconciliationDifferenceEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = DisposeReconciliationDifferenceEndpoint.OPERATION_NAME,
            requestType = DisposeReconciliationDifferenceEndpoint.Request::class,
            responseType = DisposeReconciliationDifferenceEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reconciliation-items/{itemId}/dispositions",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(DisposeReconciliationDifferenceEndpoint.Request::class).copy(
                    itemId = request.path("itemId", String::class)
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )
}
