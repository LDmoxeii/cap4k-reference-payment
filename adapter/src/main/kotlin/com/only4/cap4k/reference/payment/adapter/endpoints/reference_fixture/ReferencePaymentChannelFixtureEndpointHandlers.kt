package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferencePaymentChannelScriptRegistry
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferencePaymentChannelEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferencePaymentChannelEndpoint
import org.springframework.stereotype.Component

@Component
class ConfigureReferencePaymentChannelEndpointHandler(
    private val scripts: ReferencePaymentChannelScriptRegistry,
) : EndpointHandler<ConfigureReferencePaymentChannelEndpoint.Request, ConfigureReferencePaymentChannelEndpoint.Response> {
    override fun handle(request: ConfigureReferencePaymentChannelEndpoint.Request): ConfigureReferencePaymentChannelEndpoint.Response {
        val channelId = request.channelId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("reference payment channelId 不能为空")
        val script = request.script?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?.let {
                runCatching { ReferencePaymentChannelScriptRegistry.Script.valueOf(it) }
                    .getOrElse { throw IllegalArgumentException("不支持的 reference payment channel script：$it") }
            }
            ?: throw IllegalArgumentException("reference payment channel script 不能为空")
        return ConfigureReferencePaymentChannelEndpoint.Response(channelId, scripts.configure(channelId, script).name)
    }
}

@Component
class ResetReferencePaymentChannelEndpointHandler(
    private val scripts: ReferencePaymentChannelScriptRegistry,
) : EndpointHandler<ResetReferencePaymentChannelEndpoint.Request, ResetReferencePaymentChannelEndpoint.Response> {
    override fun handle(request: ResetReferencePaymentChannelEndpoint.Request): ResetReferencePaymentChannelEndpoint.Response {
        val channelId = request.channelId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("reference payment channelId 不能为空")
        return ResetReferencePaymentChannelEndpoint.Response(channelId, scripts.reset(channelId).name)
    }
}
