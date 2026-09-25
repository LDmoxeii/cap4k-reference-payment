package com.only4.cap4k.reference.payment.application.commands.merchant_notification

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import org.springframework.stereotype.Service

/** Thin command used by domain-event subscribers to enter the CAP4K command scope in the same UoW. */
@DesignBlockMetadata(
    tag = "command",
    name = "CreateMerchantNotificationForFact",
    packageName = "merchant_notification",
    description = "Create and deliver one idempotent merchant notification for an accepted business fact",
    aggregates = ["MerchantNotification"],
    family = "command",
)
object CreateMerchantNotificationForFactCmd {
    @Service
    class Handler(
        private val notifications: MerchantNotificationService,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val result = notifications.createAndDeliver(
                MerchantNotificationService.Intent(
                    merchantId = command.merchantId,
                    sourceKind = command.sourceKind,
                    sourceFactIdentity = command.sourceFactIdentity,
                    paymentId = command.paymentId,
                    content = command.content,
                ),
            )
            return Response(result.notification.id.toString(), result.created)
        }
    }

    data class Request(
        val merchantId: String,
        val sourceKind: String,
        val sourceFactIdentity: String,
        val paymentId: PaymentId?,
        val content: Map<String, String>,
    ) : Command<Response>

    data class Response(
        val notificationId: String,
        val created: Boolean,
    )
}
