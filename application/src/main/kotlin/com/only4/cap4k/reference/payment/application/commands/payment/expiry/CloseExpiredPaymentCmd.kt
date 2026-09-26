package com.only4.cap4k.reference.payment.application.commands.payment.expiry

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.application.manual_review.openPaymentReview
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.expire
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "CloseExpiredPayment",
    packageName = "payment.expiry",
    description = "Close one expired payment or retain pending attempts for result review",
    aggregates = ["Payment"],
    family = "command",
)
object CloseExpiredPaymentCmd {
    @Service
    class Handler(
        private val clock: Clock,
        private val operationSupport: OperationSupport,
        private val referencePolicy: ReferencePolicyService,
        private val manualReviewSupport: ManualReviewSupport,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val idempotencyKey = command.idempotencyKey.trim()
            require(merchantId.isNotBlank()) { "商户身份不能为空" }
            require(idempotencyKey.isNotBlank()) { "到期关闭幂等键不能为空" }

            val payment = Mediator.repositories.findOne(SPayment.predicateById(command.paymentId))
                ?: throw PaymentNotFoundException(command.paymentId)
            if (payment.merchantId != merchantId) {
                throw PaymentConflictException(
                    code = "PAYMENT_MERCHANT_CONFLICT",
                    message = "支付单不属于请求商户",
                    details = mapOf("paymentId" to payment.id.toString()),
                )
            }
            val requestHash = operationSupport.canonicalHash(merchantId, payment.id.toString())
            operationSupport.replayOrNull(merchantId, COMMAND_TYPE, idempotencyKey, requestHash)?.let { operation ->
                return response(payment, operationSupport.receipt(operation, replay = true))
            }

            val now = LocalDateTime.now(clock)
            if (now.isBefore(payment.expiresAt)) {
                throw PaymentConflictException(
                    code = "PAYMENT_NOT_EXPIRED",
                    message = "支付单尚未到期，不能关闭",
                    details = mapOf("paymentId" to payment.id.toString()),
                )
            }
            if (payment.status in setOf(PaymentStatus.SUCCEEDED, PaymentStatus.CLOSED, PaymentStatus.FAILED)) {
                throw PaymentConflictException(
                    code = "INVALID_STATE_TRANSITION",
                    message = "支付单当前状态不允许到期关闭",
                    details = mapOf("paymentId" to payment.id.toString(), "status" to payment.status.name),
                )
            }

            val outcome = payment.expire(now, referencePolicy.current().unknownResultReviewAfter)
            if (outcome.reviewOpenedNow) {
                outcome.reviewIdentity?.let { manualReviewSupport.openPaymentReview(payment, it) }
            }
            val receipt = operationSupport.accept(
                merchantId = merchantId,
                commandType = COMMAND_TYPE,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = requestHash,
                resourceType = "PaymentIntent",
                resourceId = payment.id.toString(),
                resourceUrl = "/api/payments/${payment.id}",
            )
            return response(payment, receipt)
        }

        private fun response(payment: Payment, receipt: OperationReceipt) = Response(
            paymentId = payment.id.toString(),
            paymentStatus = payment.status.name,
            receipt = receipt,
        )
    }

    data class Request(
        val paymentId: PaymentId,
        val merchantId: String,
        val idempotencyKey: String,
    ) : Command<Response>

    data class Response(
        val paymentId: String,
        val paymentStatus: String,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "CloseExpiredPayment"
}
