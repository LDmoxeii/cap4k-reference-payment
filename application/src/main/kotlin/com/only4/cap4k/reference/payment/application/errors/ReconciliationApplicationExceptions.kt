package com.only4.cap4k.reference.payment.application.errors

import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId

class ReconciliationBatchNotFoundException(reconciliationBatchId: ReconciliationBatchId) : PaymentApplicationException(
    code = "RECONCILIATION_BATCH_NOT_FOUND",
    message = "未找到对账批次 $reconciliationBatchId",
    details = mapOf("reconciliationBatchId" to reconciliationBatchId),
)
