package com.only4.cap4k.reference.payment.application.errors

class MerchantSettlementNotFoundException(settlementId: String) : PaymentApplicationException(
    code = "MERCHANT_SETTLEMENT_NOT_FOUND",
    message = "未找到商户结算单 $settlementId",
    details = mapOf("settlementId" to settlementId),
)

class MerchantSettlementConflictException(
    code: String,
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : PaymentApplicationException(code, message, details)

class MerchantSettlementRejectedException(
    code: String,
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : PaymentApplicationException(code, message, details)
