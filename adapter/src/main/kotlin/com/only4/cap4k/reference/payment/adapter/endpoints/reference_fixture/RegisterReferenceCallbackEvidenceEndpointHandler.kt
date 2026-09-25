package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toDomainAmount
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceInput
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceRegistry
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackKind
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.RegisterReferenceCallbackEvidenceEndpoint
import org.springframework.stereotype.Component

/** Reference-only fixture control; it does not load or mutate any business aggregate. */
@Component
class RegisterReferenceCallbackEvidenceEndpointHandler(
    private val registry: ReferenceCallbackEvidenceRegistry,
) : EndpointHandler<RegisterReferenceCallbackEvidenceEndpoint.Request, RegisterReferenceCallbackEvidenceEndpoint.Response> {
    override fun handle(
        request: RegisterReferenceCallbackEvidenceEndpoint.Request,
    ): RegisterReferenceCallbackEvidenceEndpoint.Response {
        val kind = request.kind?.trim()?.uppercase()?.let { value ->
            runCatching { ReferenceCallbackKind.valueOf(value) }
                .getOrElse { throw IllegalArgumentException("不支持的 reference callback evidence kind") }
        } ?: throw IllegalArgumentException("reference callback evidence kind 不能为空")
        val money = request.money ?: throw IllegalArgumentException("验真金额不能为空")
        val registration = registry.register(
            input = ReferenceCallbackEvidenceInput(
                kind = kind,
                channelId = required(request.channelId, "渠道标识"),
                externalIdentity = required(request.externalIdentity, "外部身份"),
                associationIdentity = required(request.associationIdentity, "关联身份"),
                amount = money.toDomainAmount(),
                currency = money.currency,
                canonicalPayload = required(request.rawPayload, "raw payload"),
            ),
            idempotencyKey = required(request.idempotencyKey, "reference fixture 幂等键"),
        )
        return RegisterReferenceCallbackEvidenceEndpoint.Response(
            evidenceId = registration.evidenceId,
            kind = registration.kind.name,
            idempotentReplay = registration.idempotentReplay,
        )
    }

    private fun required(value: String?, label: String): String = value?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("$label 不能为空")
}
