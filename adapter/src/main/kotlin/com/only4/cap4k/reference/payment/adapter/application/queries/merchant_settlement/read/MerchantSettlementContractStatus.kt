package com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read

import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.SettlementExecutionAttemptStatus

/** Maps CAP4K's transactional aggregate states to the unified reference HTTP contract. */
object MerchantSettlementContractStatus {
    fun publicStatus(status: MerchantSettlementStatus): String = when (status) {
        MerchantSettlementStatus.PREPARED,
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
        -> "READY_FOR_CONFIRMATION"
        MerchantSettlementStatus.PROCESSING -> "EXECUTING"
        MerchantSettlementStatus.SUCCEEDED -> "SETTLED"
        MerchantSettlementStatus.FAILED -> "EXECUTION_FAILED"
        MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED -> "RESULT_UNKNOWN"
        else -> status.name
    }

    fun finality(status: MerchantSettlementStatus): Finality = when (status) {
        MerchantSettlementStatus.REVIEW_REQUIRED,
        MerchantSettlementStatus.NEGATIVE_REVIEW_REQUIRED,
        MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED,
        -> Finality.REVIEW_REQUIRED
        MerchantSettlementStatus.SUCCEEDED,
        MerchantSettlementStatus.VOIDED,
        -> Finality.FINAL
        else -> Finality.NON_FINAL
    }

    fun publicExecutionStatus(status: SettlementExecutionAttemptStatus): String = when (status) {
        SettlementExecutionAttemptStatus.PROCESSING -> "SUBMITTED"
        SettlementExecutionAttemptStatus.SUCCEEDED -> "SUCCESS"
        SettlementExecutionAttemptStatus.FAILED -> "FAILURE"
        SettlementExecutionAttemptStatus.RESULT_UNKNOWN,
        SettlementExecutionAttemptStatus.REVIEW_REQUIRED,
        SettlementExecutionAttemptStatus.CONFLICT_REVIEW_REQUIRED,
        -> "UNKNOWN"
    }
}
