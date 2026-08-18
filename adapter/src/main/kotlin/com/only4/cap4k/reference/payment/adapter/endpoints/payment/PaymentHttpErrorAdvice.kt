package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.PaymentApplicationException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import jakarta.persistence.OptimisticLockException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PaymentHttpErrorAdvice {
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(error: IllegalArgumentException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", error.message)

    @ExceptionHandler(PaymentNotFoundException::class)
    fun notFound(error: PaymentNotFoundException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.NOT_FOUND, error.code, error.message)

    @ExceptionHandler(PaymentConflictException::class, NoEligibleChannelException::class)
    fun conflict(error: PaymentApplicationException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.CONFLICT, error.code, error.message)

    @ExceptionHandler(IllegalStateException::class)
    fun stateConflict(error: IllegalStateException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.CONFLICT, "PAYMENT_STATE_CONFLICT", error.message)

    @ExceptionHandler(
        OptimisticLockingFailureException::class,
        OptimisticLockException::class,
    )
    fun concurrentModification(error: RuntimeException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", error.message)

    private fun response(
        status: HttpStatus,
        code: String,
        message: String?,
    ): ResponseEntity<PaymentErrorResponse> = ResponseEntity.status(status).body(
        PaymentErrorResponse(
            status = status.value(),
            code = code,
            message = message ?: status.reasonPhrase,
        )
    )
}

data class PaymentErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
)
