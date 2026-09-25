package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_notification

import com.only4.cap4k.reference.payment.adapter.endpoints.payment.PaymentErrorResponse
import com.only4.cap4k.reference.payment.application.commands.merchant_notification.MerchantNotificationNotFoundException
import java.util.UUID
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class MerchantNotificationHttpErrorAdvice {
    @ExceptionHandler(MerchantNotificationNotFoundException::class)
    fun notFound(error: MerchantNotificationNotFoundException): ResponseEntity<PaymentErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            PaymentErrorResponse(
                code = error.code,
                message = error.message ?: "未找到商户通知",
                details = error.details,
                correlationId = UUID.randomUUID().toString(),
                retryable = false,
            ),
        )
}
