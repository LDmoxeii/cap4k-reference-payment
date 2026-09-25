package com.only4.cap4k.reference.payment.adapter.application.queries

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Reference-profile cursor integrity boundary.
 *
 * The payload remains deliberately transport-opaque and is signed with a process-held reference
 * key so callers cannot forge a different `(sortTime, id)` boundary while retaining the filter
 * fingerprint.  The ephemeral key intentionally invalidates cursors after a reference-service
 * restart; this is a learning-profile integrity guard, not a production secret-manager contract.
 */
internal object ReferenceCursorToken {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    fun encode(vararg fields: String): String {
        require(fields.none { it.contains('|') }) { "cursor field contains a reserved delimiter" }
        val payload = fields.joinToString("|").toByteArray(StandardCharsets.UTF_8)
        return "${encoder.encodeToString(payload)}.${encoder.encodeToString(sign(payload))}"
    }

    fun decode(token: String, expectedFieldCount: Int): List<String> {
        val segments = token.split('.')
        require(segments.size == 2) { "cursor token shape is invalid" }
        val payload = decoder.decode(segments[0])
        val suppliedSignature = decoder.decode(segments[1])
        require(MessageDigest.isEqual(sign(payload), suppliedSignature)) { "cursor signature is invalid" }
        return String(payload, StandardCharsets.UTF_8).split('|').also {
            require(it.size == expectedFieldCount) { "cursor field count is invalid" }
        }
    }

    private fun sign(payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(payload)
    }
}
