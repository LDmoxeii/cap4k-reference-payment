package com.only4.cap4k.reference.payment.application.commands.payment.result

import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfirmPaymentResultCmdContractTest {
    @Test
    fun `response directly owns the domain outcome and unified operation receipt`() {
        val fields = ConfirmPaymentResultCmd.Response::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .associate { it.name to it.type }

        assertEquals(2, fields.size)
        assertEquals(ChannelResultRecordingOutcome::class.java, fields["outcome"])
        assertEquals(OperationReceipt::class.java, fields["receipt"])
    }
}
