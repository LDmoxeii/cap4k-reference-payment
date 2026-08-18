package com.only4.cap4k.reference.payment.application.commands.payment.result

import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfirmPaymentResultCmdContractTest {
    @Test
    fun `response directly owns the domain outcome without a copied application dto`() {
        val fields = ConfirmPaymentResultCmd.Response::class.java.declaredFields.filterNot { it.isSynthetic }

        assertEquals(1, fields.size)
        assertEquals("outcome", fields.single().name)
        assertEquals(ChannelResultRecordingOutcome::class.java, fields.single().type)
    }
}
