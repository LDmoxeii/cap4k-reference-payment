package com.only4.cap4k.reference.payment.application.errors

open class PaymentApplicationException(
    val code: String,
    message: String,
) : RuntimeException(message)

class PaymentNotFoundException(paymentId: String) : PaymentApplicationException(
    code = "PAYMENT_NOT_FOUND",
    message = "payment $paymentId was not found",
)

class PaymentConflictException(code: String, message: String) : PaymentApplicationException(code, message)

class NoEligibleChannelException(paymentIdentity: String) : PaymentApplicationException(
    code = "NO_ELIGIBLE_CHANNEL",
    message = "no eligible merchant channel configuration for $paymentIdentity",
)
