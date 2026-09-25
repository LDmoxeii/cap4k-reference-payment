package com.only4.cap4k.reference.payment.adapter.reference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReferenceActorContextResolverTest {
    private val resolver = ReferenceActorContextResolver(ReferenceActorRegistry())

    @Test
    fun `trusted actor context is one shot`() {
        resolver.bind(ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS)

        assertEquals(
            ReferenceActorContext("reference-payment-reviewer", "PAYMENT_REVIEW_OPERATOR"),
            resolver.consume(),
        )
        assertFailsWith<ReferenceActorContextException> { resolver.consume() }
    }

    @Test
    fun `missing or unknown alias clears a previously bound context`() {
        resolver.bind(ReferenceActorRegistry.PAYMENT_REVIEWER_ALIAS)

        assertFailsWith<ReferenceActorContextException> { resolver.bind(null) }
        assertFailsWith<ReferenceActorContextException> { resolver.consume() }

        resolver.bind(ReferenceActorRegistry.REFUND_REVIEWER_ALIAS)
        assertFailsWith<ReferenceActorContextException> { resolver.bind("unknown-reference-actor") }
        assertFailsWith<ReferenceActorContextException> { resolver.consume() }
    }
}
