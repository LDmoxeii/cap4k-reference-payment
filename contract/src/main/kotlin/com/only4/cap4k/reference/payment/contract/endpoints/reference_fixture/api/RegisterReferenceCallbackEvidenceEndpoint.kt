package com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money

/**
 * Reference-profile test control only. It pre-registers server-held callback evidence and is not
 * a merchant/channel business API.
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "RegisterReferenceCallbackEvidenceEndpoint",
    packageName = "reference_fixture.api",
    description = "POST /api/reference-fixtures/callback-evidence",
    aggregates = [],
    operationName = "reference-fixture.callback-evidence.register",
    family = "endpoint",
)
object RegisterReferenceCallbackEvidenceEndpoint {
    const val OPERATION_NAME: String = "reference-fixture.callback-evidence.register"

    /** Nullable wire fields let the handler return the unified VALIDATION_ERROR for omissions. */
    data class Request(
        val idempotencyKey: String?,
        val kind: String?,
        val channelId: String?,
        val externalIdentity: String?,
        val associationIdentity: String?,
        val money: Money?,
        val rawPayload: String?,
    ) : EndpointRequest<Response>

    data class Response(
        val evidenceId: String,
        val kind: String,
        val idempotentReplay: Boolean,
    )
}
