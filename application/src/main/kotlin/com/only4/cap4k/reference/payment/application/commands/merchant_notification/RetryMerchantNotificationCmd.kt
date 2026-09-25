package com.only4.cap4k.reference.payment.application.commands.merchant_notification

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_notification.SMerchantNotification
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotificationId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationStatus
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "RetryMerchantNotification",
    packageName = "merchant_notification",
    description = "Retry a failed notification with the same frozen notification and content identities",
    aggregates = ["MerchantNotification"],
    family = "command",
)
object RetryMerchantNotificationCmd {
    @Service
    class Handler(
        private val notifications: MerchantNotificationService,
        private val operations: OperationSupport,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val key = command.idempotencyKey.trim()
            require(key.isNotBlank() && key.length <= 128) { "通知重试幂等键不能为空且不能超过 128 字符" }
            val notification = Mediator.repositories.findOne(
                SMerchantNotification.predicateById(command.notificationId),
            ) ?: throw MerchantNotificationNotFoundException(command.notificationId.toString())
            val hash = operations.canonicalHash(command.notificationId.toString())
            operations.replayOrNull(notification.merchantId, COMMAND_TYPE, key, hash)?.let { original ->
                return Response(notification.id.toString(), operations.receipt(original, replay = true))
            }
            if (notification.status != MerchantNotificationStatus.FAILED) {
                throw PaymentConflictException(
                    "NOTIFICATION_DELIVERY_NOT_RETRYABLE",
                    "只有明确失败的通知可以重试",
                    mapOf("notificationId" to notification.id.toString(), "status" to notification.status.name),
                )
            }
            if (notification.deliveryAttempts.size >= notification.maxAttempts) {
                throw PaymentConflictException(
                    "NOTIFICATION_ATTEMPTS_EXHAUSTED",
                    "通知已达到冻结的最多投递次数",
                    mapOf("notificationId" to notification.id.toString()),
                )
            }
            notifications.retry(notification.id)
            return Response(
                notification.id.toString(),
                operations.accept(
                    merchantId = notification.merchantId,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = key,
                    canonicalRequestHash = hash,
                    resourceType = "MerchantNotification",
                    resourceId = notification.id.toString(),
                    resourceUrl = "/api/merchant-notifications/${notification.id}",
                ),
            )
        }
    }

    data class Request(
        val notificationId: MerchantNotificationId,
        val idempotencyKey: String,
    ) : Command<Response>

    data class Response(
        val notificationId: String,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "RetryMerchantNotification"
}

class MerchantNotificationNotFoundException(notificationId: String) :
    com.only4.cap4k.reference.payment.application.errors.PaymentApplicationException(
        code = "MERCHANT_NOTIFICATION_NOT_FOUND",
        message = "未找到商户通知 $notificationId",
        details = mapOf("notificationId" to notificationId),
    )
