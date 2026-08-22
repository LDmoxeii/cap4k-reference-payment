package com.only4.cap4k.reference.payment.application.errors

class MerchantSettlementNotFoundException(settlementId: String) : PaymentApplicationException(
    code = "MERCHANT_SETTLEMENT_NOT_FOUND",
    message = "merchant settlement $settlementId was not found",
)

class MerchantSettlementConflictException(code: String, message: String) : PaymentApplicationException(code, message)

class MerchantSettlementRejectedException(code: String, message: String) : PaymentApplicationException(code, message)
