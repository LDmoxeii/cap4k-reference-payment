package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ConfirmMerchantSettlementEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ConfirmMerchantSettlementResultEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.GetMerchantSettlementEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.PrepareMerchantSettlementEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.StartMerchantSettlementExecutionEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.VoidMerchantSettlementEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class MerchantSettlementEndpointHttpConfiguration {
    @Bean
    fun prepareMerchantSettlementHttpBinding(): EndpointMvcBinding<PrepareMerchantSettlementEndpoint.Request, PrepareMerchantSettlementEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = PrepareMerchantSettlementEndpoint.OPERATION_NAME,
            requestType = PrepareMerchantSettlementEndpoint.Request::class,
            responseType = PrepareMerchantSettlementEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/merchant-settlements",
            requestMapper = EndpointMvcRequestMapper { request -> request.body(PrepareMerchantSettlementEndpoint.Request::class) },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 201),
        )

    @Bean
    fun confirmMerchantSettlementHttpBinding(): EndpointMvcBinding<ConfirmMerchantSettlementEndpoint.Request, ConfirmMerchantSettlementEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = ConfirmMerchantSettlementEndpoint.OPERATION_NAME,
            requestType = ConfirmMerchantSettlementEndpoint.Request::class,
            responseType = ConfirmMerchantSettlementEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/merchant-settlements/{settlementId}/confirmations",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(ConfirmMerchantSettlementEndpoint.Request::class).copy(
                    settlementId = request.path("settlementId", String::class)
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun startMerchantSettlementExecutionHttpBinding(): EndpointMvcBinding<StartMerchantSettlementExecutionEndpoint.Request, StartMerchantSettlementExecutionEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = StartMerchantSettlementExecutionEndpoint.OPERATION_NAME,
            requestType = StartMerchantSettlementExecutionEndpoint.Request::class,
            responseType = StartMerchantSettlementExecutionEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/merchant-settlements/{settlementId}/executions",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(StartMerchantSettlementExecutionEndpoint.Request::class).copy(
                    settlementId = request.path("settlementId", String::class)
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun confirmMerchantSettlementResultHttpBinding(): EndpointMvcBinding<ConfirmMerchantSettlementResultEndpoint.Request, ConfirmMerchantSettlementResultEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ConfirmMerchantSettlementResultEndpoint.OPERATION_NAME,
            requestType = ConfirmMerchantSettlementResultEndpoint.Request::class,
            responseType = ConfirmMerchantSettlementResultEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/channel/settlement-results",
        )

    @Bean
    fun voidMerchantSettlementHttpBinding(): EndpointMvcBinding<VoidMerchantSettlementEndpoint.Request, VoidMerchantSettlementEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = VoidMerchantSettlementEndpoint.OPERATION_NAME,
            requestType = VoidMerchantSettlementEndpoint.Request::class,
            responseType = VoidMerchantSettlementEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/merchant-settlements/{settlementId}/voids",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(VoidMerchantSettlementEndpoint.Request::class).copy(
                    settlementId = request.path("settlementId", String::class)
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun getMerchantSettlementHttpBinding(): EndpointMvcBinding<GetMerchantSettlementEndpoint.Request, GetMerchantSettlementEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetMerchantSettlementEndpoint.OPERATION_NAME,
            requestType = GetMerchantSettlementEndpoint.Request::class,
            responseType = GetMerchantSettlementEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/merchant-settlements/{settlementId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetMerchantSettlementEndpoint.Request(request.path("settlementId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )
}
