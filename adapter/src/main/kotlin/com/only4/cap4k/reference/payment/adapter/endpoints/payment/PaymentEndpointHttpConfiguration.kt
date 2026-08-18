package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.ConfirmPaymentResultEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.CreatePaymentEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.GetPaymentEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.StartPaymentAttemptEndpoint
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class PaymentEndpointHttpConfiguration {

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
    fun startPaymentAttemptHttpBinding(): EndpointMvcBinding<StartPaymentAttemptEndpoint.Request, StartPaymentAttemptEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = StartPaymentAttemptEndpoint.OPERATION_NAME,
            requestType = StartPaymentAttemptEndpoint.Request::class,
            responseType = StartPaymentAttemptEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/payments/{paymentId}/attempts",
            requestMapper = EndpointMvcRequestMapper { request ->
                StartPaymentAttemptEndpoint.Request(paymentId = request.path("paymentId", String::class))
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
}
