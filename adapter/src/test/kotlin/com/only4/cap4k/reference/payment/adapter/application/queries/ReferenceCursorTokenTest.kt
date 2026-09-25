package com.only4.cap4k.reference.payment.adapter.application.queries

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ReferenceCursorTokenTest {

    @Test
    fun `round trips an opaque signed keyset boundary`() {
        val token = ReferenceCursorToken.encode(
            "v1",
            "filter-fingerprint",
            "2026-09-24T00:00:00Z",
            "01900000-0000-7000-8000-000000000001",
        )

        assertContentEquals(
            listOf("v1", "filter-fingerprint", "2026-09-24T00:00:00Z", "01900000-0000-7000-8000-000000000001"),
            ReferenceCursorToken.decode(token, 4),
        )
    }

    @Test
    fun `rejects forged payload and forged signature`() {
        val token = ReferenceCursorToken.encode("v1", "filter", "2026-09-24T00:00:00Z", "item-1")
        val segments = token.split('.')
        val forgedPayload = "${flip(segments[0])}.${segments[1]}"
        val forgedSignature = "${segments[0]}.${flip(segments[1])}"

        assertFailsWith<IllegalArgumentException> { ReferenceCursorToken.decode(forgedPayload, 4) }
        assertFailsWith<IllegalArgumentException> { ReferenceCursorToken.decode(forgedSignature, 4) }
    }

    private fun flip(value: String): String =
        (if (value.first() == 'A') 'B' else 'A') + value.drop(1)
}
