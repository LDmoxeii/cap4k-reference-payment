package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementConflictException
import com.only4.cap4k.reference.payment.application.errors.AuthoritativeBillNotFoundException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementRejectedException
import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.application.errors.ManualReviewNotFoundException
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.OperationNotFoundException
import com.only4.cap4k.reference.payment.application.errors.OperationResourceNotFoundException
import com.only4.cap4k.reference.payment.application.errors.PaymentApplicationException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundConflictException
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundRejectedException
import com.only4.cap4k.reference.payment.application.errors.ResourceNotReadyException
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.domain.aggregates.payment.RefundBudgetConflictException
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceActorContextException
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceCallbackEvidenceConflictException
import jakarta.persistence.OptimisticLockException
import jakarta.persistence.PersistenceException
import java.util.UUID
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
            code = "VALIDATION_ERROR",
            message = chineseOrFallback(error.message, "请求参数不合法，请检查后重试"),
        )

    @ExceptionHandler(ReferenceActorContextException::class)
    fun actorContext(error: ReferenceActorContextException): ResponseEntity<PaymentErrorResponse> =
        response(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = chineseOrFallback(error.message, "缺少或无法识别可信操作人上下文"),
        )

    @ExceptionHandler(ReferenceCallbackEvidenceConflictException::class)
    fun referenceFixtureConflict(error: ReferenceCallbackEvidenceConflictException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.CONFLICT, error.code, error.message, error.details)

    @ExceptionHandler(InvalidCursorException::class)
    fun invalidCursor(error: InvalidCursorException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.BAD_REQUEST, error.code, error.message, error.details)

    @ExceptionHandler(PaymentImmutableMutationException::class)
    fun immutablePaymentMutation(error: PaymentImmutableMutationException): ResponseEntity<PaymentErrorResponse> =
        response(
            status = HttpStatus.METHOD_NOT_ALLOWED,
            code = "INVALID_STATE_TRANSITION",
            message = error.message,
        )

    @ExceptionHandler(
        PaymentNotFoundException::class,
        AuthoritativeBillNotFoundException::class,
        RefundNotFoundException::class,
        ReconciliationBatchNotFoundException::class,
        MerchantSettlementNotFoundException::class,
        ManualReviewNotFoundException::class,
        OperationNotFoundException::class,
        OperationResourceNotFoundException::class,
    )
    fun notFound(error: PaymentApplicationException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.NOT_FOUND, error.code, error.message, error.details)

    @ExceptionHandler(ResourceNotReadyException::class)
    fun resourceNotReady(error: ResourceNotReadyException): ResponseEntity<PaymentErrorResponse> =
        response(HttpStatus.CONFLICT, error.code, error.message, error.details, retryable = true)

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
            code = if (error.message?.startsWith("INVALID_STATE_TRANSITION:") == true) {
                "INVALID_STATE_TRANSITION"
            } else {
                "PAYMENT_STATE_CONFLICT"
            },
            message = chineseOrFallback(error.message, "当前业务状态不允许执行该操作"),
        )

    @ExceptionHandler(RefundBudgetConflictException::class)
    fun refundBudgetConflict(error: RefundBudgetConflictException): ResponseEntity<PaymentErrorResponse> =
        response(
            status = HttpStatus.CONFLICT,
            code = "REFUND_BUDGET_EXCEEDED",
            message = chineseOrFallback(error.message, "退款金额超过当前可退款预算"),
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
        when (error.javaClass.name) {
            "org.springframework.web.servlet.resource.NoResourceFoundException" -> return response(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "请求的业务资源或操作不存在",
            )
        }
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
        retryable: Boolean = false,
    ): ResponseEntity<PaymentErrorResponse> = ResponseEntity.status(status).body(
        PaymentErrorResponse(
            code = code,
            message = message ?: "请求处理失败",
            details = details,
            correlationId = UUID.randomUUID().toString(),
            retryable = retryable,
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

data class PaymentErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any?> = emptyMap(),
    val correlationId: String,
    val retryable: Boolean,
)
