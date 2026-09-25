package com.only4.cap4k.reference.payment.adapter.reference

import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

enum class ReferenceCallbackKind {
    PAYMENT,
    REFUND,
    SETTLEMENT,
}

data class ReferenceCallbackEvidenceInput(
    val kind: ReferenceCallbackKind,
    val channelId: String,
    val externalIdentity: String,
    val associationIdentity: String,
    val amount: BigDecimal,
    val currency: String,
    val canonicalPayload: String,
)

data class ReferenceCallbackVerification(
    val verified: Boolean,
    val reason: String,
)

/** Result returned only by the reference fixture control plane. */
data class ReferenceCallbackEvidenceRegistration(
    val evidenceId: String,
    val kind: ReferenceCallbackKind,
    val idempotentReplay: Boolean,
)

/**
 * A fixture registration is not a business command, but it still must never silently overwrite
 * a previously configured deterministic evidence record.
 */
class ReferenceCallbackEvidenceConflictException(
    val code: String,
    message: String,
    val details: Map<String, Any?> = emptyMap(),
) : RuntimeException(message)

/** Adapter contract used by all channel-result capability handlers. */
fun interface ReferenceCallbackVerifier {
    fun verify(input: ReferenceCallbackEvidenceInput): ReferenceCallbackVerification
}

/**
 * Server-held sandbox evidence. A test/fixture may register deterministic expected evidence,
 * but a business callback can only submit its observable payload and can never submit a verdict
 * or a proof to compare.
 */
@Component
class ReferenceCallbackEvidenceRegistry : ReferenceCallbackVerifier {
    private val expected = ConcurrentHashMap<EvidenceKey, ExpectedEvidence>()
    private val registrations = ConcurrentHashMap<String, Registration>()

    /**
     * Reference-only fixture registration. The control caller supplies observable callback facts
     * (including its raw payload), while this process generates and retains the verifier proof.
     * The proof is neither returned nor accepted by the business callback endpoint.
     */
    @Synchronized
    fun register(input: ReferenceCallbackEvidenceInput, idempotencyKey: String): ReferenceCallbackEvidenceRegistration {
        val normalized = normalize(input)
        val normalizedIdempotencyKey = idempotencyKey.trim()
        require(normalizedIdempotencyKey.isNotBlank()) { "reference fixture 幂等键不能为空" }
        val canonicalRequest = canonicalRequest(normalized)
        registrations[normalizedIdempotencyKey]?.let { existing ->
            if (existing.canonicalRequest != canonicalRequest) {
                throw ReferenceCallbackEvidenceConflictException(
                    code = "IDEMPOTENCY_CONFLICT",
                    message = "reference fixture 幂等键已绑定到内容不同的 callback evidence",
                    details = mapOf("evidenceId" to existing.evidenceId),
                )
            }
            return ReferenceCallbackEvidenceRegistration(
                evidenceId = existing.evidenceId,
                kind = normalized.kind,
                idempotentReplay = true,
            )
        }

        val evidenceKey = keyOf(normalized)
        val existingEvidence = expected[evidenceKey]
        if (existingEvidence != null && !matches(existingEvidence, normalized)) {
            throw ReferenceCallbackEvidenceConflictException(
                code = "REFERENCE_EVIDENCE_CONFLICT",
                message = "相同渠道通知已登记内容不同的 reference callback evidence",
                details = mapOf(
                    "kind" to normalized.kind.name,
                    "channelId" to normalized.channelId,
                    "externalIdentity" to normalized.externalIdentity,
                ),
            )
        }

        val evidence = existingEvidence ?: ExpectedEvidence(
            evidenceId = evidenceIdFor(evidenceKey),
            associationIdentity = normalized.associationIdentity,
            amount = normalized.amount,
            currency = normalized.currency,
            canonicalPayload = normalized.canonicalPayload,
            // Deliberately process-held and never part of a request/response contract.
            serverHeldProof = evidenceIdFor(EvidenceKey(normalized.kind, normalized.channelId, "proof:${normalized.externalIdentity}")),
        ).also { expected[evidenceKey] = it }
        registrations[normalizedIdempotencyKey] = Registration(
            evidenceId = evidence.evidenceId,
            canonicalRequest = canonicalRequest,
        )
        return ReferenceCallbackEvidenceRegistration(
            evidenceId = evidence.evidenceId,
            kind = normalized.kind,
            idempotentReplay = false,
        )
    }

    override fun verify(input: ReferenceCallbackEvidenceInput): ReferenceCallbackVerification {
        val normalized = try {
            normalize(input)
        } catch (error: IllegalArgumentException) {
            return ReferenceCallbackVerification(false, error.message ?: "callback evidence 格式不合法")
        }
        val evidence = expected[keyOf(normalized)]
            ?: return ReferenceCallbackVerification(false, "未找到服务端 reference callback evidence")
        val matches = evidence.serverHeldProof.isNotBlank() && matches(evidence, normalized)
        return if (matches) {
            ReferenceCallbackVerification(true, "服务端 reference evidence 核验通过")
        } else {
            ReferenceCallbackVerification(false, "callback 与服务端冻结 evidence 不一致")
        }
    }

    fun clearForFixture() {
        expected.clear()
        registrations.clear()
    }

    private fun normalize(input: ReferenceCallbackEvidenceInput): ReferenceCallbackEvidenceInput {
        require(input.channelId.isNotBlank()) { "渠道标识不能为空" }
        require(input.externalIdentity.isNotBlank()) { "外部身份不能为空" }
        require(input.associationIdentity.isNotBlank()) { "关联身份不能为空" }
        require(input.amount.signum() > 0) { "验真金额必须为正数" }
        require(input.currency.isNotBlank()) { "验真币种不能为空" }
        require(input.canonicalPayload.isNotBlank()) { "raw payload 不能为空" }
        return input.copy(
            channelId = input.channelId.trim(),
            externalIdentity = input.externalIdentity.trim(),
            associationIdentity = input.associationIdentity.trim(),
            amount = input.amount.stripTrailingZeros(),
            currency = input.currency.trim().uppercase(),
        )
    }

    private fun matches(evidence: ExpectedEvidence, input: ReferenceCallbackEvidenceInput): Boolean =
        evidence.associationIdentity == input.associationIdentity &&
            evidence.amount.compareTo(input.amount) == 0 &&
            evidence.currency == input.currency &&
            evidence.canonicalPayload == input.canonicalPayload

    private fun canonicalRequest(input: ReferenceCallbackEvidenceInput): String = listOf(
        input.kind.name,
        input.channelId,
        input.externalIdentity,
        input.associationIdentity,
        input.amount.toPlainString(),
        input.currency,
        input.canonicalPayload,
    ).joinToString("|") { value -> "${value.length}:$value" }

    private fun evidenceIdFor(key: EvidenceKey): String = "ref-evidence-" + MessageDigest.getInstance("SHA-256")
        .digest("${key.kind}|${key.channelId}|${key.externalIdentity}".toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(24)

    private fun keyOf(input: ReferenceCallbackEvidenceInput) = EvidenceKey(
        kind = input.kind,
        channelId = input.channelId.trim(),
        externalIdentity = input.externalIdentity.trim(),
    )

    private data class EvidenceKey(
        val kind: ReferenceCallbackKind,
        val channelId: String,
        val externalIdentity: String,
    )

    private data class ExpectedEvidence(
        val evidenceId: String,
        val associationIdentity: String,
        val amount: BigDecimal,
        val currency: String,
        val canonicalPayload: String,
        val serverHeldProof: String,
    )

    private data class Registration(
        val evidenceId: String,
        val canonicalRequest: String,
    )
}
