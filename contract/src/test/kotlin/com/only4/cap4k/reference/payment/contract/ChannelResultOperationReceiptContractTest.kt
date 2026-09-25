package com.only4.cap4k.reference.payment.contract

import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.ConfirmMerchantSettlementResultEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.ConfirmPaymentResultEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.ConfirmRefundResultEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelResultOperationReceiptContractTest {
    @Test
    fun `all channel result responses expose one strongly typed operation receipt`() {
        listOf<Class<*>>(
            ConfirmPaymentResultEndpoint.Response::class.java,
            ConfirmRefundResultEndpoint.Response::class.java,
            ConfirmMerchantSettlementResultEndpoint.Response::class.java,
        ).forEach { responseType ->
            val receipt = responseType.getDeclaredField("receipt")
            assertEquals(OperationReceipt::class.java, receipt.type)
        }
    }
}
