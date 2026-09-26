package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceSettlementExecutorScriptRegistry
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferenceSettlementExecutorEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.GetReferenceSettlementExecutorEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ResetReferenceSettlementExecutorEndpoint
import org.springframework.stereotype.Component

@Component
class ConfigureReferenceSettlementExecutorEndpointHandler(
    private val scripts: ReferenceSettlementExecutorScriptRegistry,
) : EndpointHandler<ConfigureReferenceSettlementExecutorEndpoint.Request, ConfigureReferenceSettlementExecutorEndpoint.Response> {
    override fun handle(request: ConfigureReferenceSettlementExecutorEndpoint.Request): ConfigureReferenceSettlementExecutorEndpoint.Response {
        val id = request.executionId ?: ""
        val raw = request.script?.trim()?.uppercase()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("reference settlement executor script 不能为空")
        val script = runCatching { ReferenceSettlementExecutorScriptRegistry.Script.valueOf(raw) }
            .getOrElse { throw IllegalArgumentException("不支持的 reference settlement executor script：$raw") }
        return scripts.configure(id, script).let {
            ConfigureReferenceSettlementExecutorEndpoint.Response(
                it.executionId, it.script.name, it.configured, it.consumed, it.observation, it.diagnosticSummary,
            )
        }
    }
}

@Component
class ResetReferenceSettlementExecutorEndpointHandler(
    private val scripts: ReferenceSettlementExecutorScriptRegistry,
) : EndpointHandler<ResetReferenceSettlementExecutorEndpoint.Request, ResetReferenceSettlementExecutorEndpoint.Response> {
    override fun handle(request: ResetReferenceSettlementExecutorEndpoint.Request): ResetReferenceSettlementExecutorEndpoint.Response =
        scripts.reset(request.executionId ?: "").let {
            ResetReferenceSettlementExecutorEndpoint.Response(
                it.executionId, it.script.name, it.configured, it.consumed, it.observation, it.diagnosticSummary,
            )
        }
}

@Component
class GetReferenceSettlementExecutorEndpointHandler(
    private val scripts: ReferenceSettlementExecutorScriptRegistry,
) : EndpointHandler<GetReferenceSettlementExecutorEndpoint.Request, GetReferenceSettlementExecutorEndpoint.Response> {
    override fun handle(request: GetReferenceSettlementExecutorEndpoint.Request): GetReferenceSettlementExecutorEndpoint.Response =
        scripts.read(request.executionId).let {
            GetReferenceSettlementExecutorEndpoint.Response(
                it.executionId, it.script.name, it.configured, it.consumed, it.observation, it.diagnosticSummary,
            )
        }
}
