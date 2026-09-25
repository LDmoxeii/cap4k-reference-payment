package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_notification

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_notification.read.MerchantNotificationReadModel
import com.only4.cap4k.reference.payment.application.commands.merchant_notification.RetryMerchantNotificationCmd
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.GetMerchantNotificationEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.ListMerchantNotificationsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.RetryMerchantNotificationEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotificationId
import org.springframework.stereotype.Component

@Component
class GetMerchantNotificationEndpointHandler(
    private val readModel: MerchantNotificationReadModel,
) : EndpointHandler<GetMerchantNotificationEndpoint.Request, GetMerchantNotificationEndpoint.Response> {
    override fun handle(request: GetMerchantNotificationEndpoint.Request): GetMerchantNotificationEndpoint.Response =
        GetMerchantNotificationEndpoint.Response(readModel.detail(request.notificationId))
}

@Component
class ListMerchantNotificationsEndpointHandler(
    private val readModel: MerchantNotificationReadModel,
) : EndpointHandler<ListMerchantNotificationsEndpoint.Request, ListMerchantNotificationsEndpoint.Response> {
    override fun handle(request: ListMerchantNotificationsEndpoint.Request): ListMerchantNotificationsEndpoint.Response =
        readModel.list(request)
}

@Component
class RetryMerchantNotificationEndpointHandler :
    EndpointHandler<RetryMerchantNotificationEndpoint.Request, RetryMerchantNotificationEndpoint.Response> {
    override fun handle(request: RetryMerchantNotificationEndpoint.Request): RetryMerchantNotificationEndpoint.Response {
        val result = Mediator.commands.send(
            RetryMerchantNotificationCmd.Request(
                notificationId = MerchantNotificationId.parse(request.notificationId),
                idempotencyKey = request.idempotencyKey,
            ),
        )
        return RetryMerchantNotificationEndpoint.Response(result.notificationId, result.receipt)
    }
}
