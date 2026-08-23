package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.fasterxml.jackson.annotation.JsonInclude
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementConflictException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementRejectedException
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.PaymentApplicationException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundConflictException
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundRejectedException
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.domain.aggregates.payment.RefundBudgetConflictException
import jakarta.persistence.OptimisticLockException
import jakarta.persistence.PersistenceException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PaymentHttpErrorAdvice {
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(error: IllegalArgumentException): ResponseEntity<PaymentErrorResponse> =
        response(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = chineseOrFallback(error.message, "请求参数不合法，请检查后重试"),
        )

    @ExceptionHandler(
        PaymentNotFoundException::class,
        RefundNotFoundException::class,
        ReconciliationBatchNotFoundException::class,
        MerchantSettlementNotFoundException::class,
    )
    fun notFound(error: PaymentApplicationException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.NOT_FOUND, error.code, error.message, error.details)

    @ExceptionHandler(
        PaymentConflictException::class,
        NoEligibleChannelException::class,
        RefundConflictException::class,
        RefundRejectedException::class,
        MerchantSettlementConflictException::class,
        MerchantSettlementRejectedException::class,
    )
    fun conflict(error: PaymentApplicationException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.CONFLICT, error.code, error.message, error.details)

    @ExceptionHandler(IllegalStateException::class)
    fun stateConflict(error: IllegalStateException): ResponseEntity<PaymentErrorResponse> =
        response(
            status = HttpStatus.CONFLICT,
            code = "PAYMENT_STATE_CONFLICT",
            message = chineseOrFallback(error.message, "当前业务状态不允许执行该操作"),
        )

    @ExceptionHandler(RefundBudgetConflictException::class)
    fun refundBudgetConflict(error: RefundBudgetConflictException): ResponseEntity<PaymentErrorResponse> =
        response(
            status = HttpStatus.CONFLICT,
            code = "CONCURRENT_MODIFICATION",
            message = chineseOrFallback(error.message, "退款预算已被并发修改，请刷新后重试"),
        )

    @ExceptionHandler(
        OptimisticLockingFailureException::class,
        OptimisticLockException::class,
        DataIntegrityViolationException::class,
        PersistenceException::class,
    )
    fun concurrentModification(error: RuntimeException): ResponseEntity<PaymentErrorResponse> {
        log.warn("并发或持久化冲突已映射为安全响应", error)
        return response(
            status = HttpStatus.CONFLICT,
            code = "CONCURRENT_MODIFICATION",
            message = "数据已被其他请求修改，请刷新后重试",
        )
    }

    @ExceptionHandler(Exception::class)
    fun internalError(error: Exception): ResponseEntity<PaymentErrorResponse> {
        log.error("未处理异常已映射为安全响应", error)
        return response(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_ERROR",
            message = "系统处理请求时发生异常，请稍后重试",
        )
    }

    private fun response(
        status: HttpStatus,
        code: String,
        message: String?,
        details: Map<String, Any?> = emptyMap(),
    ): ResponseEntity<PaymentErrorResponse> = ResponseEntity.status(status).body(
        PaymentErrorResponse(
            status = status.value(),
            code = code,
            message = message ?: "请求处理失败",
            details = details,
        )
    )

    /**
     * `IllegalArgumentException`/`IllegalStateException` 仍可能来自第三方或内部不变量。
     * 只有本地受控的中文消息可以直接展示；英文数据库、provider 或框架原文统一降级，
     * 避免把实现细节意外固化为公开 API，也避免泄露 SQL、类名和内部拓扑。
     */
    private fun chineseOrFallback(message: String?, fallback: String): String =
        message?.takeIf { value -> value.any { it in '\u4e00'..'\u9fff' } } ?: fallback

    private companion object {
        val log = LoggerFactory.getLogger(PaymentHttpErrorAdvice::class.java)
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class PaymentErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
    val details: Map<String, Any?> = emptyMap(),
)
