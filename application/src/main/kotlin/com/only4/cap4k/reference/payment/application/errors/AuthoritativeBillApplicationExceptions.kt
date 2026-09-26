package com.only4.cap4k.reference.payment.application.errors

import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBillId

class AuthoritativeBillNotFoundException(billId: AuthoritativeBillId) : PaymentApplicationException(
    code = "AUTHORITATIVE_BILL_NOT_FOUND",
    message = "未找到权威账单 $billId",
    details = mapOf("billId" to billId.toString()),
)
