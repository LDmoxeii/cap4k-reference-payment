package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceMerchantNotificationSenderRegistry
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ClearReferenceMerchantNotificationSenderEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferenceMerchantNotificationSenderEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferenceMerchantNotificationSenderEndpoint
import org.springframework.stereotype.Component

@Component
class ConfigureReferenceMerchantNotificationSenderEndpointHandler(
    private val registry: ReferenceMerchantNotificationSenderRegistry,
) : EndpointHandler<ConfigureReferenceMerchantNotificationSenderEndpoint.Request, ConfigureReferenceMerchantNotificationSenderEndpoint.Response> {
    override fun handle(request: ConfigureReferenceMerchantNotificationSenderEndpoint.Request):
        ConfigureReferenceMerchantNotificationSenderEndpoint.Response {
        val selector = SenderSelector.from(request.notificationIdentity, request.sourceKind, request.sourceFactIdentity)
        val script = runCatching { ReferenceMerchantNotificationSenderRegistry.Script.valueOf(request.script.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("不支持的通知 sender script") }
        if (selector.notificationIdentity != null) registry.configureForNotification(selector.notificationIdentity, script)
        else registry.configureForSource(selector.sourceKind!!, selector.sourceFactIdentity!!, script)
        return ConfigureReferenceMerchantNotificationSenderEndpoint.Response(
            selector.notificationIdentity, selector.sourceKind, selector.sourceFactIdentity, script.name,
        )
    }
}

@Component
class ResetReferenceMerchantNotificationSenderEndpointHandler(
    private val registry: ReferenceMerchantNotificationSenderRegistry,
) : EndpointHandler<ResetReferenceMerchantNotificationSenderEndpoint.Request, ResetReferenceMerchantNotificationSenderEndpoint.Response> {
    override fun handle(request: ResetReferenceMerchantNotificationSenderEndpoint.Request):
        ResetReferenceMerchantNotificationSenderEndpoint.Response {
        val selector = SenderSelector.from(request.notificationIdentity, request.sourceKind, request.sourceFactIdentity)
        if (selector.notificationIdentity != null) registry.resetForNotification(selector.notificationIdentity)
        else registry.resetForSource(selector.sourceKind!!, selector.sourceFactIdentity!!)
        return ResetReferenceMerchantNotificationSenderEndpoint.Response(
            selector.notificationIdentity, selector.sourceKind, selector.sourceFactIdentity,
            ReferenceMerchantNotificationSenderRegistry.DEFAULT.name,
        )
    }
}

@Component
class ClearReferenceMerchantNotificationSenderEndpointHandler(
    private val registry: ReferenceMerchantNotificationSenderRegistry,
) : EndpointHandler<ClearReferenceMerchantNotificationSenderEndpoint.Request, ClearReferenceMerchantNotificationSenderEndpoint.Response> {
    override fun handle(request: ClearReferenceMerchantNotificationSenderEndpoint.Request):
        ClearReferenceMerchantNotificationSenderEndpoint.Response {
        registry.clearForFixture()
        return ClearReferenceMerchantNotificationSenderEndpoint.Response(cleared = true)
    }
}

private data class SenderSelector(
    val notificationIdentity: String?,
    val sourceKind: String?,
    val sourceFactIdentity: String?,
) {
    companion object {
        fun from(notificationIdentity: String?, sourceKind: String?, sourceFactIdentity: String?): SenderSelector {
            val identity = notificationIdentity?.trim()?.takeIf(String::isNotEmpty)
            val kind = sourceKind?.trim()?.uppercase()?.takeIf(String::isNotEmpty)
            val fact = sourceFactIdentity?.trim()?.takeIf(String::isNotEmpty)
            require((identity != null && kind == null && fact == null) ||
                (identity == null && kind != null && fact != null)) {
                "必须且只能指定 notificationIdentity 或 sourceKind + sourceFactIdentity"
            }
            if (kind != null) require(kind in setOf("PAYMENT", "REFUND", "RECONCILIATION", "SETTLEMENT")) {
                "不支持的通知来源类型"
            }
            return SenderSelector(identity, kind, fact)
        }
    }
}
