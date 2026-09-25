package com.only4.cap4k.reference.payment.application.commands.merchant_notification

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.capabilities.merchant_notification.sender.SendMerchantNotification
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_notification.SMerchantNotification
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotification
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotificationDeliveryAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.MerchantNotificationId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.factory.MerchantNotificationFactory
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.nextDeliveryIdentity
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.recordDelivery
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/**
 * Called inside the source command's CAP4K Unit of Work. The intent and its first delivery
 * observation become visible together with the source fact. Failed sends remain observable and
 * can be retried explicitly; duplicate source commands never send a second notification.
 */
@Service
class MerchantNotificationService(
    private val clock: Clock,
    private val referencePolicy: ReferencePolicyService,
) {
    fun createAndDeliver(request: Intent): Result {
        val normalized = normalize(request)
        val existing = Mediator.repositories.findOne(
            SMerchantNotification.predicate { schema -> schema.notificationIdentity eq normalized.identity },
        )
        if (existing != null) {
            require(
                existing.contentIdentity == normalized.fingerprint &&
                    existing.content == normalized.content &&
                    existing.merchantId == normalized.merchantId &&
                    existing.sourceKind == normalized.sourceKind &&
                    existing.sourceFactIdentity == normalized.sourceFactIdentity &&
                    existing.paymentId == request.paymentId
            ) { "NOTIFICATION_IDENTITY_CONFLICT" }
            return Result(existing, existing.deliveryAttempts.firstOrNull(), created = false)
        }

        return createAndDeliverNew(request, normalized)
    }

    private fun createAndDeliverNew(request: Intent, normalized: NormalizedIntent): Result {

        val notification = Mediator.factories.create<MerchantNotificationFactory.Payload, MerchantNotification>(
            MerchantNotificationFactory.Payload(
                notificationIdentity = normalized.identity,
                contentIdentity = normalized.fingerprint,
                merchantId = normalized.merchantId,
                sourceKind = normalized.sourceKind,
                sourceFactIdentity = normalized.sourceFactIdentity,
                paymentId = request.paymentId,
                content = normalized.content,
                maxAttempts = referencePolicy.current().merchantNotificationMaxAttempts,
            ),
        )
        return Result(notification, deliver(notification), created = true)
    }

    private fun normalize(request: Intent): NormalizedIntent {
        val sourceKind = required(request.sourceKind, "sourceKind").uppercase()
        require(sourceKind in SOURCE_KINDS) { "不支持的通知来源类型：$sourceKind" }
        val sourceFactIdentity = required(request.sourceFactIdentity, "sourceFactIdentity")
        val merchantId = required(request.merchantId, "merchantId")
        val content = canonicalContent(request.content)
        require(content.length <= 4096) { "通知内容过长" }
        return NormalizedIntent(
            identity = notificationIdentity(sourceKind, sourceFactIdentity),
            fingerprint = contentIdentity(content),
            merchantId = merchantId,
            sourceKind = sourceKind,
            sourceFactIdentity = sourceFactIdentity,
            content = content,
        )
    }

    /**
     * Explicit retry uses the next stable identity. Passing a previous identity replays its stored
     * observation without contacting the sender; another new identity cannot skip a sequence.
     */
    fun retry(notificationId: MerchantNotificationId, deliveryIdentity: String? = null): Result {
        val notification = Mediator.repositories.findOne(SMerchantNotification.predicateById(notificationId))
            ?: throw IllegalArgumentException("MERCHANT_NOTIFICATION_NOT_FOUND")
        val requestedIdentity = deliveryIdentity?.let { required(it, "deliveryIdentity") }
        notification.deliveryAttempts.firstOrNull { it.deliveryIdentity == requestedIdentity }?.let {
            return Result(notification, it, created = false)
        }
        val nextIdentity = notification.nextDeliveryIdentity()
        require(requestedIdentity == null || requestedIdentity == nextIdentity) {
            "NOTIFICATION_DELIVERY_IDENTITY_CONFLICT"
        }
        return Result(notification, deliver(notification, nextIdentity), created = false)
    }

    private fun deliver(
        notification: MerchantNotification,
        deliveryIdentity: String = notification.nextDeliveryIdentity(),
    ): MerchantNotificationDeliveryAttempt {
        val response = Mediator.capabilities.call(
            SendMerchantNotification.Request(
                notificationId = notification.id,
                notificationIdentity = notification.notificationIdentity,
                contentIdentity = notification.contentIdentity,
                deliveryIdentity = deliveryIdentity,
                merchantId = notification.merchantId,
                sourceKind = notification.sourceKind,
                sourceFactIdentity = notification.sourceFactIdentity,
                content = notification.content,
            ),
        )
        return notification.recordDelivery(
            deliveryIdentity = deliveryIdentity,
            outcome = response.outcome,
            diagnostic = response.diagnostic,
            recordedAt = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC),
        )
    }

    data class Intent(
        val merchantId: String,
        val sourceKind: String,
        val sourceFactIdentity: String,
        val paymentId: PaymentId?,
        /** A flat immutable fact snapshot; keys are sorted and escaped before hashing. */
        val content: Map<String, String>,
    )

    data class Result(
        val notification: MerchantNotification,
        val deliveryAttempt: MerchantNotificationDeliveryAttempt?,
        val created: Boolean,
    )

    private data class NormalizedIntent(
        val identity: String,
        val fingerprint: String,
        val merchantId: String,
        val sourceKind: String,
        val sourceFactIdentity: String,
        val content: String,
    )

    companion object {
        private val SOURCE_KINDS = setOf("PAYMENT", "REFUND", "RECONCILIATION", "SETTLEMENT")

        fun notificationIdentity(sourceKind: String, sourceFactIdentity: String): String =
            "merchant-notification:${required(sourceKind, "sourceKind").uppercase()}:${required(sourceFactIdentity, "sourceFactIdentity")}"

        fun contentIdentity(canonicalContent: String): String = MessageDigest.getInstance("SHA-256")
            .digest(canonicalContent.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        fun canonicalContent(content: Map<String, String>): String {
            require(content.isNotEmpty()) { "通知内容不能为空" }
            return content.toSortedMap().entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
                require(key.isNotBlank()) { "通知内容字段名不能为空" }
                "${quote(key)}:${quote(value)}"
            }
        }

        private fun required(value: String, name: String): String =
            value.trim().also { require(it.isNotBlank()) { "$name 不能为空" } }

        private fun quote(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                }
            }
            append('"')
        }
    }
}
