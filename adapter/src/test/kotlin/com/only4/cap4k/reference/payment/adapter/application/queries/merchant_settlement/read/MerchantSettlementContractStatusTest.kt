package com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read

import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MerchantSettlementContractStatusTest {
    @Test
    fun `settled aggregate is exposed as SETTLED and FINAL`() {
        assertEquals("SETTLED", MerchantSettlementContractStatus.publicStatus(MerchantSettlementStatus.SUCCEEDED))
        assertEquals(Finality.FINAL, MerchantSettlementContractStatus.finality(MerchantSettlementStatus.SUCCEEDED))
    }

    @Test
    fun `execution attempts use unified result vocabulary`() {
        assertEquals("SUBMITTED", MerchantSettlementContractStatus.publicExecutionStatus(SettlementExecutionAttemptStatus.PROCESSING))
        assertEquals("SUCCESS", MerchantSettlementContractStatus.publicExecutionStatus(SettlementExecutionAttemptStatus.SUCCEEDED))
        assertEquals("FAILURE", MerchantSettlementContractStatus.publicExecutionStatus(SettlementExecutionAttemptStatus.FAILED))
        assertEquals("UNKNOWN", MerchantSettlementContractStatus.publicExecutionStatus(SettlementExecutionAttemptStatus.RESULT_UNKNOWN))
    }

    @Test
    fun `review states retain review finality while using public lifecycle statuses`() {
        assertEquals(
            "READY_FOR_CONFIRMATION",
            MerchantSettlementContractStatus.publicStatus(MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED),
        )
        assertEquals(
            Finality.REVIEW_REQUIRED,
            MerchantSettlementContractStatus.finality(MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED),
        )
        assertEquals(
            "RESULT_UNKNOWN",
            MerchantSettlementContractStatus.publicStatus(MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED),
        )
    }
}
