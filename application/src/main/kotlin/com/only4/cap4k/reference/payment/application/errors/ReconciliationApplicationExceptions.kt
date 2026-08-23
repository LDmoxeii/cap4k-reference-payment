package com.only4.cap4k.reference.payment.application.errors

class ReconciliationBatchNotFoundException(batchId: String) : PaymentApplicationException(
    code = "RECONCILIATION_BATCH_NOT_FOUND",
    message = "未找到对账批次 $batchId",
    details = mapOf("batchId" to batchId),
)
