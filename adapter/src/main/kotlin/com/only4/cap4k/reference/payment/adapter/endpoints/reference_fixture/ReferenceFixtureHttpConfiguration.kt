package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.AdvanceReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ClearReferenceMerchantNotificationSenderEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferenceMerchantNotificationSenderEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferenceMerchantChannelEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferencePaymentChannelEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.CreateReferenceOperationFixtureEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferenceOperationFixtureResourceEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.RegisterReferenceCallbackEvidenceEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.RegisterReferenceAuthoritativeBillRevisionEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferenceMerchantNotificationSenderEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferencePolicyEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.RunReferenceMaintenanceEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferencePaymentChannelEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.SetReferenceClockEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.SignalReferenceAuthoritativeBillAvailableEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.TransitionReferenceOperationFixtureEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

/** HTTP bindings whose route names intentionally make their reference-fixture-only scope explicit. */
@Configuration(proxyBeanMethods = false)
class ReferenceFixtureHttpConfiguration {
    @Bean
    fun configureReferenceMerchantChannelHttpBinding(): EndpointMvcBinding<
        ConfigureReferenceMerchantChannelEndpoint.Request,
        ConfigureReferenceMerchantChannelEndpoint.Response,
    > = EndpointMvcBinding.json(
        operationName = ConfigureReferenceMerchantChannelEndpoint.OPERATION_NAME,
        requestType = ConfigureReferenceMerchantChannelEndpoint.Request::class,
        responseType = ConfigureReferenceMerchantChannelEndpoint.Response::class,
        method = HttpMethod.POST,
        path = "/api/reference-fixtures/merchant-channels",
    )

    @Bean
    fun runReferenceMaintenanceHttpBinding(): EndpointMvcBinding<
        RunReferenceMaintenanceEndpoint.Request,
        RunReferenceMaintenanceEndpoint.Response,
    > = EndpointMvcBinding.json(
        operationName = RunReferenceMaintenanceEndpoint.OPERATION_NAME,
        requestType = RunReferenceMaintenanceEndpoint.Request::class,
        responseType = RunReferenceMaintenanceEndpoint.Response::class,
        method = HttpMethod.POST,
        path = "/api/reference-fixtures/maintenance",
    )

    @Bean
    fun createReferenceOperationFixtureHttpBinding(): EndpointMvcBinding<
        CreateReferenceOperationFixtureEndpoint.Request,
        CreateReferenceOperationFixtureEndpoint.Response,
    > = EndpointMvcBinding.json(
        operationName = CreateReferenceOperationFixtureEndpoint.OPERATION_NAME,
        requestType = CreateReferenceOperationFixtureEndpoint.Request::class,
        responseType = CreateReferenceOperationFixtureEndpoint.Response::class,
        method = HttpMethod.POST,
        path = "/api/reference-fixtures/operations",
    )

    @Bean
    fun transitionReferenceOperationFixtureHttpBinding(): EndpointMvcBinding<
        TransitionReferenceOperationFixtureEndpoint.Request,
        TransitionReferenceOperationFixtureEndpoint.Response,
    > = EndpointMvcBinding.special(
        operationName = TransitionReferenceOperationFixtureEndpoint.OPERATION_NAME,
        requestType = TransitionReferenceOperationFixtureEndpoint.Request::class,
        responseType = TransitionReferenceOperationFixtureEndpoint.Response::class,
        method = HttpMethod.POST,
        path = "/api/reference-fixtures/operations/{operationId}/transitions",
        requestMapper = EndpointMvcRequestMapper { request ->
            request.body(TransitionReferenceOperationFixtureEndpoint.Request::class).copy(
                operationId = request.path("operationId", String::class),
            )
        },
        responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
    )

    @Bean
    fun getReferenceOperationFixtureResourceHttpBinding(): EndpointMvcBinding<
        GetReferenceOperationFixtureResourceEndpoint.Request,
        GetReferenceOperationFixtureResourceEndpoint.Response,
    > = EndpointMvcBinding.special(
        operationName = GetReferenceOperationFixtureResourceEndpoint.OPERATION_NAME,
        requestType = GetReferenceOperationFixtureResourceEndpoint.Request::class,
        responseType = GetReferenceOperationFixtureResourceEndpoint.Response::class,
        method = HttpMethod.GET,
        path = "/api/reference-fixtures/operation-resources/{resourceId}",
        requestMapper = EndpointMvcRequestMapper { request ->
            GetReferenceOperationFixtureResourceEndpoint.Request(
                request.path("resourceId", String::class),
            )
        },
        responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
    )

    @Bean
    fun configureReferenceMerchantNotificationSenderHttpBinding():
        EndpointMvcBinding<ConfigureReferenceMerchantNotificationSenderEndpoint.Request, ConfigureReferenceMerchantNotificationSenderEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ConfigureReferenceMerchantNotificationSenderEndpoint.OPERATION_NAME,
            requestType = ConfigureReferenceMerchantNotificationSenderEndpoint.Request::class,
            responseType = ConfigureReferenceMerchantNotificationSenderEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/merchant-notification-sender-script",
        )

    @Bean
    fun resetReferenceMerchantNotificationSenderHttpBinding():
        EndpointMvcBinding<ResetReferenceMerchantNotificationSenderEndpoint.Request, ResetReferenceMerchantNotificationSenderEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ResetReferenceMerchantNotificationSenderEndpoint.OPERATION_NAME,
            requestType = ResetReferenceMerchantNotificationSenderEndpoint.Request::class,
            responseType = ResetReferenceMerchantNotificationSenderEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/merchant-notification-sender-script/reset",
        )

    @Bean
    fun clearReferenceMerchantNotificationSenderHttpBinding():
        EndpointMvcBinding<ClearReferenceMerchantNotificationSenderEndpoint.Request, ClearReferenceMerchantNotificationSenderEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = ClearReferenceMerchantNotificationSenderEndpoint.OPERATION_NAME,
            requestType = ClearReferenceMerchantNotificationSenderEndpoint.Request::class,
            responseType = ClearReferenceMerchantNotificationSenderEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/merchant-notification-sender-script/clear",
            requestMapper = EndpointMvcRequestMapper { ClearReferenceMerchantNotificationSenderEndpoint.Request() },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun configureReferencePaymentChannelHttpBinding():
        EndpointMvcBinding<ConfigureReferencePaymentChannelEndpoint.Request, ConfigureReferencePaymentChannelEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ConfigureReferencePaymentChannelEndpoint.OPERATION_NAME,
            requestType = ConfigureReferencePaymentChannelEndpoint.Request::class,
            responseType = ConfigureReferencePaymentChannelEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/payment-channel-script",
        )

    @Bean
    fun resetReferencePaymentChannelHttpBinding():
        EndpointMvcBinding<ResetReferencePaymentChannelEndpoint.Request, ResetReferencePaymentChannelEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ResetReferencePaymentChannelEndpoint.OPERATION_NAME,
            requestType = ResetReferencePaymentChannelEndpoint.Request::class,
            responseType = ResetReferencePaymentChannelEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/payment-channel-script/reset",
        )

    @Bean
    fun registerReferenceAuthoritativeBillRevisionHttpBinding():
        EndpointMvcBinding<RegisterReferenceAuthoritativeBillRevisionEndpoint.Request, RegisterReferenceAuthoritativeBillRevisionEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = RegisterReferenceAuthoritativeBillRevisionEndpoint.OPERATION_NAME,
            requestType = RegisterReferenceAuthoritativeBillRevisionEndpoint.Request::class,
            responseType = RegisterReferenceAuthoritativeBillRevisionEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/bills",
        )

    @Bean
    fun signalReferenceAuthoritativeBillAvailableHttpBinding():
        EndpointMvcBinding<SignalReferenceAuthoritativeBillAvailableEndpoint.Request, SignalReferenceAuthoritativeBillAvailableEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = SignalReferenceAuthoritativeBillAvailableEndpoint.OPERATION_NAME,
            requestType = SignalReferenceAuthoritativeBillAvailableEndpoint.Request::class,
            responseType = SignalReferenceAuthoritativeBillAvailableEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/bills/{billIdentity}/signals",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(SignalReferenceAuthoritativeBillAvailableEndpoint.Request::class).copy(
                    billIdentity = request.path("billIdentity", String::class),
                )
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun registerReferenceCallbackEvidenceHttpBinding():
        EndpointMvcBinding<RegisterReferenceCallbackEvidenceEndpoint.Request, RegisterReferenceCallbackEvidenceEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = RegisterReferenceCallbackEvidenceEndpoint.OPERATION_NAME,
            requestType = RegisterReferenceCallbackEvidenceEndpoint.Request::class,
            responseType = RegisterReferenceCallbackEvidenceEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/callback-evidence",
            requestMapper = EndpointMvcRequestMapper { request ->
                request.body(RegisterReferenceCallbackEvidenceEndpoint.Request::class)
            },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 201),
        )

    @Bean
    fun getReferencePolicyHttpBinding(): EndpointMvcBinding<GetReferencePolicyEndpoint.Request, GetReferencePolicyEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetReferencePolicyEndpoint.OPERATION_NAME,
            requestType = GetReferencePolicyEndpoint.Request::class,
            responseType = GetReferencePolicyEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/reference-fixtures/policy",
            requestMapper = EndpointMvcRequestMapper { GetReferencePolicyEndpoint.Request() },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun configureReferencePolicyHttpBinding(): EndpointMvcBinding<ConfigureReferencePolicyEndpoint.Request, ConfigureReferencePolicyEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = ConfigureReferencePolicyEndpoint.OPERATION_NAME,
            requestType = ConfigureReferencePolicyEndpoint.Request::class,
            responseType = ConfigureReferencePolicyEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/policy",
        )

    @Bean
    fun resetReferencePolicyHttpBinding(): EndpointMvcBinding<ResetReferencePolicyEndpoint.Request, ResetReferencePolicyEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = ResetReferencePolicyEndpoint.OPERATION_NAME,
            requestType = ResetReferencePolicyEndpoint.Request::class,
            responseType = ResetReferencePolicyEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/policy/reset",
            requestMapper = EndpointMvcRequestMapper { ResetReferencePolicyEndpoint.Request() },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun getReferenceClockHttpBinding(): EndpointMvcBinding<GetReferenceClockEndpoint.Request, GetReferenceClockEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetReferenceClockEndpoint.OPERATION_NAME,
            requestType = GetReferenceClockEndpoint.Request::class,
            responseType = GetReferenceClockEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/api/reference-fixtures/clock",
            requestMapper = EndpointMvcRequestMapper { GetReferenceClockEndpoint.Request() },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )

    @Bean
    fun setReferenceClockHttpBinding(): EndpointMvcBinding<SetReferenceClockEndpoint.Request, SetReferenceClockEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = SetReferenceClockEndpoint.OPERATION_NAME,
            requestType = SetReferenceClockEndpoint.Request::class,
            responseType = SetReferenceClockEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/clock/set",
        )

    @Bean
    fun advanceReferenceClockHttpBinding(): EndpointMvcBinding<AdvanceReferenceClockEndpoint.Request, AdvanceReferenceClockEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = AdvanceReferenceClockEndpoint.OPERATION_NAME,
            requestType = AdvanceReferenceClockEndpoint.Request::class,
            responseType = AdvanceReferenceClockEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/clock/advance",
        )

    @Bean
    fun resetReferenceClockHttpBinding(): EndpointMvcBinding<ResetReferenceClockEndpoint.Request, ResetReferenceClockEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = ResetReferenceClockEndpoint.OPERATION_NAME,
            requestType = ResetReferenceClockEndpoint.Request::class,
            responseType = ResetReferenceClockEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/reference-fixtures/clock/reset",
            requestMapper = EndpointMvcRequestMapper { ResetReferenceClockEndpoint.Request() },
            responsePolicy = EndpointMvcResponsePolicy.response(status = 200),
        )
}
