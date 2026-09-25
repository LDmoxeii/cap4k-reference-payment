package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_notification

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.GetMerchantNotificationEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.ListMerchantNotificationsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.RetryMerchantNotificationEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class MerchantNotificationEndpointHttpConfiguration {
    @Bean
    fun getMerchantNotificationHttpBinding():
        EndpointMvcBinding<GetMerchantNotificationEndpoint.Request, GetMerchantNotificationEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetMerchantNotificationEndpoint.OPERATION_NAME,
            requestType = GetMerchantNotificationEndpoint.Request::class,
            responseType = GetMerchantNotificationEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/merchant-notifications/{notificationId}",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetMerchantNotificationEndpoint.Request(request.path("notificationId", String::class))
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun listMerchantNotificationsHttpBinding():
        EndpointMvcBinding<ListMerchantNotificationsEndpoint.Request, ListMerchantNotificationsEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ListMerchantNotificationsEndpoint.OPERATION_NAME,
            requestType = ListMerchantNotificationsEndpoint.Request::class,
            responseType = ListMerchantNotificationsEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/merchant-notifications/search",
        )

    @Bean
    fun retryMerchantNotificationHttpBinding():
        EndpointMvcBinding<RetryMerchantNotificationEndpoint.Request, RetryMerchantNotificationEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = RetryMerchantNotificationEndpoint.OPERATION_NAME,
            requestType = RetryMerchantNotificationEndpoint.Request::class,
            responseType = RetryMerchantNotificationEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/merchant-notifications/{notificationId}/retries",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(RetryMerchantNotificationEndpoint.Request::class).copy(
                    notificationId = request.path("notificationId", String::class),
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )
}
