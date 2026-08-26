package com.only4.cap4k.reference.payment.application.errors

import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId

open class PaymentApplicationException(
    val code: String,
    message: String,
    val details: Map<String, Any?> = emptyMap(),
) : RuntimeException(message)

class PaymentNotFoundException(paymentId: PaymentId) : PaymentApplicationException(
    code = "PAYMENT_NOT_FOUND",
    message = "未找到支付单 $paymentId",
    details = mapOf("paymentId" to paymentId),
)

class PaymentConflictException(
    code: String,
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : PaymentApplicationException(code, message, details)

class NoEligibleChannelException(paymentIdentity: String) : PaymentApplicationException(
    code = "NO_ELIGIBLE_CHANNEL",
    message = "支付 $paymentIdentity 没有符合条件的商户渠道配置",
    details = mapOf("paymentIdentity" to paymentIdentity),
)

class RefundNotFoundException(refundId: RefundId) : PaymentApplicationException(
    code = "REFUND_NOT_FOUND",
    message = "未找到退款单 $refundId",
    details = mapOf("refundId" to refundId),
)

class RefundConflictException(
    code: String,
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : PaymentApplicationException(code, message, details)

class RefundRejectedException(
    code: String,
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : PaymentApplicationException(code, message, details)
