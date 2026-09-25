package com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotification
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "MerchantNotification",
    name = "MerchantNotificationFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.factory",
    description = "",
    type = "factory",
    root = false,
)
class MerchantNotificationFactory : AggregateFactory<MerchantNotificationFactory.Payload, MerchantNotification> {
    override fun create(entityPayload: Payload): MerchantNotification {
        require(entityPayload.notificationIdentity.isNotBlank()) { "通知身份不能为空" }
        require(entityPayload.contentIdentity.isNotBlank()) { "通知内容身份不能为空" }
        require(entityPayload.merchantId.isNotBlank()) { "通知商户不能为空" }
        require(entityPayload.sourceKind.isNotBlank()) { "通知来源类型不能为空" }
        require(entityPayload.sourceFactIdentity.isNotBlank()) { "通知来源事实身份不能为空" }
        require(entityPayload.content.isNotBlank()) { "通知内容不能为空" }
        require(entityPayload.maxAttempts > 0) { "最多投递次数必须为正数" }
        return MerchantNotification(
            notificationIdentity = entityPayload.notificationIdentity,
            contentIdentity = entityPayload.contentIdentity,
            merchantId = entityPayload.merchantId,
            sourceKind = entityPayload.sourceKind,
            sourceFactIdentity = entityPayload.sourceFactIdentity,
            paymentId = entityPayload.paymentId,
            content = entityPayload.content,
            status = MerchantNotificationStatus.PENDING,
            finality = "NON_FINAL",
            maxAttempts = entityPayload.maxAttempts,
        )
    }

    data class Payload(
        val notificationIdentity: String,
        val contentIdentity: String,
        val merchantId: String,
        val sourceKind: String,
        val sourceFactIdentity: String,
        val paymentId: PaymentId?,
        val content: String,
        val maxAttempts: Int,
    ) : AggregatePayload<MerchantNotification>
}
