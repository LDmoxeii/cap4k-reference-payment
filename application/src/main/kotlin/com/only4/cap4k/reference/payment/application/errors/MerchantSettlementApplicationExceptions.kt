package com.only4.cap4k.reference.payment.application.errors

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId

class MerchantSettlementNotFoundException(merchantSettlementId: MerchantSettlementId) : PaymentApplicationException(
    code = "MERCHANT_SETTLEMENT_NOT_FOUND",
    message = "未找到商户结算单 $merchantSettlementId",
    details = mapOf("merchantSettlementId" to merchantSettlementId),
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
