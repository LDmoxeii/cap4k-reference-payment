package com.only4.cap4k.reference.payment.application.errors

class ReconciliationBatchNotFoundException(batchId: String) : PaymentApplicationException(
    code = "RECONCILIATION_BATCH_NOT_FOUND",
    message = "reconciliation batch $batchId was not found",
)
