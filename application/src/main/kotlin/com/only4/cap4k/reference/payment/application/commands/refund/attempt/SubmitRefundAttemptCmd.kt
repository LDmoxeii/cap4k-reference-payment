package com.only4.cap4k.reference.payment.application.commands.refund.attempt

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.refund.gateway.StartChannelRefund
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.releaseRefundReservation
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.markAttemptSubmitted
import com.only4.cap4k.reference.payment.domain.aggregates.refund.markAttemptResultUnknown
import com.only4.cap4k.reference.payment.domain.aggregates.refund.markChannelAccepted
import com.only4.cap4k.reference.payment.domain.aggregates.refund.rejectAttemptStart
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

@DesignBlockMetadata(
    tag = "command",
    name = "SubmitRefundAttempt",
    packageName = "refund.attempt",
    description = "Submit one created refund attempt to the reference channel without reserving budget again",
    aggregates = ["Payment", "Refund"],
    family = "command",
)
object SubmitRefundAttemptCmd {

    @Service
    class Handler(
        private val clock: Clock,
        private val operationSupport: OperationSupport,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val idempotencyKey = command.idempotencyKey.trim()
            require(idempotencyKey.isNotBlank()) { "退款尝试提交幂等键不能为空" }
            val refund = Mediator.repositories.findOne(
                SRefund.predicateById(command.refundId),
            ) ?: throw RefundNotFoundException(command.refundId)
            val attempt = refund.attempts.firstOrNull { it.id == command.refundAttemptId }
                ?: throw IllegalArgumentException("退款尝试 ${command.refundAttemptId} 不属于退款单 ${refund.id}")
            val requestHash = operationSupport.canonicalHash(refund.id.toString(), attempt.id.toString())
            operationSupport.replayOrNull(
                merchantId = refund.merchantId,
                commandType = COMMAND_TYPE,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = requestHash,
            )?.let { operation ->
                val replayedAttempt = refund.attempts.firstOrNull { it.id.toString() == operation.resourceId }
                    ?: throw IllegalStateException("operation ${operation.id} refers to a refund attempt outside refund ${refund.id}")
                return response(refund, replayedAttempt, "已重放退款尝试受理", operationSupport.receipt(operation, replay = true))
            }
            if (attempt.status != RefundAttemptStatus.CREATED) {
                return response(
                    refund,
                    attempt,
                    "退款尝试已提交或已终结，不重复调用渠道",
                    accept(refund, attempt, idempotencyKey, requestHash),
                )
            }

            refund.markAttemptSubmitted(attempt.id)
            val gateway = try {
                Mediator.capabilities.call(
                    StartChannelRefund.Request(
                        refundAttemptId = attempt.id.toString(),
                        channelId = attempt.channelId,
                        requestIdentity = attempt.requestIdentity,
                        amount = refund.amount,
                        currency = refund.currency,
                    ),
                )
            } catch (error: RuntimeException) {
                log.warn("退款渠道调用失败：refundId={}, attemptId={}", refund.id, attempt.id, error)
                val diagnostic = "退款渠道调用结果未知，保留原请求身份等待收敛"
                refund.markAttemptResultUnknown(attempt.id, diagnostic)
                return response(refund, attempt, diagnostic, accept(refund, attempt, idempotencyKey, requestHash))
            }

            val diagnostic = refundGatewaySummary(gateway.accepted, gateway.failureCode)
            if (!gateway.accepted || gateway.channelRefundId.isNullOrBlank()) {
                gateway.diagnosticSummary?.takeIf { it.isNotBlank() }?.let {
                    log.warn("退款渠道拒绝原始诊断仅记录日志：refundId={}, attemptId={}, diagnostic={}", refund.id, attempt.id, it)
                }
                releaseReservationIfNeeded(refund.id.toString(), refund.paymentId.toString(), refund.amount, refund.rejectAttemptStart(
                    attempt.id,
                    gateway.failureCode ?: "CHANNEL_REJECTED",
                    diagnostic,
                    LocalDateTime.now(clock),
                ))
                return response(refund, attempt, diagnostic, accept(refund, attempt, idempotencyKey, requestHash))
            }

            refund.markChannelAccepted(attempt.id, gateway.channelRefundId, LocalDateTime.now(clock))
            return response(refund, attempt, diagnostic, accept(refund, attempt, idempotencyKey, requestHash))
        }

        private fun accept(
            refund: com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund,
            attempt: com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttempt,
            idempotencyKey: String,
            requestHash: String,
        ): OperationReceipt = operationSupport.accept(
            merchantId = refund.merchantId,
            commandType = COMMAND_TYPE,
            idempotencyKey = idempotencyKey,
            canonicalRequestHash = requestHash,
            resourceType = "RefundAttempt",
            resourceId = attempt.id.toString(),
            resourceUrl = "/api/refunds/${refund.id}",
        )

        private fun releaseReservationIfNeeded(
            refundId: String,
            paymentId: String,
            amount: java.math.BigDecimal,
            releasedNow: Boolean,
        ) {
            if (!releasedNow) return
            val payment = Mediator.repositories.findOne(SPayment.predicateById(
                com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId.parse(paymentId),
            )) ?: throw PaymentNotFoundException(
                com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId.parse(paymentId),
            )
            payment.releaseRefundReservation(amount)
            log.info("退款渠道提交被明确拒绝，已释放一次预算：refundId={}, paymentId={}", refundId, paymentId)
        }
    }

    private fun response(
        refund: com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund,
        attempt: com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttempt,
        diagnosticSummary: String?,
        receipt: OperationReceipt,
    ) = Response(
        refundId = refund.id.toString(),
        refundAttemptId = attempt.id.toString(),
        refundStatus = refund.status.name,
        attemptStatus = attempt.status.name,
        channelId = attempt.channelId,
        requestIdentity = attempt.requestIdentity,
        diagnosticSummary = diagnosticSummary,
        receipt = receipt,
    )

    private fun refundGatewaySummary(accepted: Boolean, failureCode: String?): String = when {
        accepted -> "退款渠道已受理请求"
        failureCode?.trim()?.uppercase() == "UNSUPPORTED_CHANNEL" -> "退款渠道不支持当前请求"
        else -> "退款渠道拒绝了请求"
    }

    private val log = LoggerFactory.getLogger(SubmitRefundAttemptCmd::class.java)

    data class Request(
        val refundId: RefundId,
        val refundAttemptId: RefundAttemptId,
        val idempotencyKey: String,
    ) : Command<Response>

    data class Response(
        val refundId: String,
        val refundAttemptId: String,
        val refundStatus: String,
        val attemptStatus: String,
        val channelId: String,
        val requestIdentity: String,
        val diagnosticSummary: String?,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "SubmitRefundAttempt"
}
