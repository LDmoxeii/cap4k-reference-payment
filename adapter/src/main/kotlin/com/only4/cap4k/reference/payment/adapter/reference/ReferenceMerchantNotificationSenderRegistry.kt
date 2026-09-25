package com.only4.cap4k.reference.payment.adapter.reference

import com.only4.cap4k.reference.payment.application.capabilities.merchant_notification.sender.SendMerchantNotification
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationDeliveryOutcome
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Reference-only sender control. Scripts affect only delivery observations, never the source
 * business fact. An observed delivery identity always replays its original outcome and content.
 */
@Component
class ReferenceMerchantNotificationSenderRegistry {
    enum class Script { SUCCESS, FAILURE, RESULT_UNKNOWN }

    private val notificationScripts = ConcurrentHashMap<String, Script>()
    private val sourceScripts = ConcurrentHashMap<Source, Script>()
    private val observations = ConcurrentHashMap<String, Observation>()

    fun configureForNotification(notificationIdentity: String, script: Script): Script =
        script.also { notificationScripts[required(notificationIdentity, "notificationIdentity")] = it }

    fun configureForSource(sourceKind: String, sourceFactIdentity: String, script: Script): Script =
        script.also { sourceScripts[source(sourceKind, sourceFactIdentity)] = it }

    fun resetForNotification(notificationIdentity: String) {
        val normalized = required(notificationIdentity, "notificationIdentity")
        notificationScripts.remove(normalized)
    }

    fun resetForSource(sourceKind: String, sourceFactIdentity: String) {
        val normalized = source(sourceKind, sourceFactIdentity)
        sourceScripts.remove(normalized)
    }

    /** A clean fixture run may clear both scripts and sender observations. */
    @Synchronized
    fun clearForFixture() {
        notificationScripts.clear()
        sourceScripts.clear()
        observations.clear()
    }

    fun scriptFor(notificationIdentity: String, sourceKind: String, sourceFactIdentity: String): Script =
        notificationScripts[notificationIdentity.trim()]
            ?: sourceScripts[Source(sourceKind.trim().uppercase(), sourceFactIdentity.trim())]
            ?: DEFAULT

    @Synchronized
    fun send(request: SendMerchantNotification.Request): SendMerchantNotification.Response {
        val deliveryIdentity = required(request.deliveryIdentity, "deliveryIdentity")
        val fingerprint = listOf(
            required(request.notificationIdentity, "notificationIdentity"),
            required(request.contentIdentity, "contentIdentity"),
            required(request.merchantId, "merchantId"),
            required(request.sourceKind, "sourceKind").uppercase(),
            required(request.sourceFactIdentity, "sourceFactIdentity"),
            request.content,
        ).joinToString("|") { "${it.length}:$it" }
        observations[deliveryIdentity]?.let { existing ->
            require(existing.fingerprint == fingerprint) { "NOTIFICATION_DELIVERY_IDENTITY_CONFLICT" }
            return existing.response
        }
        val script = scriptFor(request.notificationIdentity, request.sourceKind, request.sourceFactIdentity)
        val response = when (script) {
            Script.SUCCESS -> SendMerchantNotification.Response(MerchantNotificationDeliveryOutcome.SUCCESS, null)
            Script.FAILURE -> SendMerchantNotification.Response(
                MerchantNotificationDeliveryOutcome.FAILURE,
                "reference sender scripted failure",
            )
            Script.RESULT_UNKNOWN -> SendMerchantNotification.Response(
                MerchantNotificationDeliveryOutcome.RESULT_UNKNOWN,
                "reference sender result unknown",
            )
        }
        observations[deliveryIdentity] = Observation(fingerprint, response)
        return response
    }

    fun observedDeliveryCount(): Int = observations.size

    private fun source(kind: String, factIdentity: String) =
        Source(required(kind, "sourceKind").uppercase(), required(factIdentity, "sourceFactIdentity"))

    private fun required(value: String, name: String): String =
        value.trim().also { require(it.isNotBlank()) { "$name 不能为空" } }

    private data class Source(val kind: String, val factIdentity: String)
    private data class Observation(
        val fingerprint: String,
        val response: SendMerchantNotification.Response,
    )

    companion object {
        val DEFAULT = Script.SUCCESS
    }
}
