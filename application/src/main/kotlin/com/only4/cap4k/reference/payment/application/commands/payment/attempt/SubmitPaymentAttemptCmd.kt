package com.only4.cap4k.reference.payment.application.commands.payment.attempt

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.payment.gateway.StartChannelPayment
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.freezeAttemptSubmission
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordAttemptSubmission
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "SubmitPaymentAttempt",
    packageName = "payment.attempt",
    description = "Freeze a stable submission identity then submit one created payment attempt to the reference channel",
    aggregates = ["Payment"],
    family = "command",
)
object SubmitPaymentAttemptCmd {
    @Service
    class Handler(
        private val clock: Clock,
        private val operationSupport: OperationSupport,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val idempotencyKey = command.idempotencyKey.trim()
            require(idempotencyKey.isNotBlank()) { "支付尝试提交幂等键不能为空" }
            val payment = Mediator.repositories.findOne(SPayment.predicateById(command.paymentId))
                ?: throw PaymentNotFoundException(command.paymentId)
            val attempt = payment.attempts.firstOrNull { it.id == command.paymentAttemptId }
                ?: throw IllegalArgumentException("支付尝试 ${command.paymentAttemptId} 不属于支付单 ${payment.id}")
            val requestHash = operationSupport.canonicalHash(payment.id.toString(), attempt.id.toString())
            operationSupport.replayOrNull(payment.merchantId, COMMAND_TYPE, idempotencyKey, requestHash)?.let { operation ->
                val replayed = payment.attempts.firstOrNull { it.id.toString() == operation.resourceId }
                    ?: throw IllegalStateException("operation ${operation.id} refers to a payment attempt outside payment ${payment.id}")
                return response(payment, replayed, operationSupport.receipt(operation, replay = true))
            }
            if (attempt.status != PaymentAttemptStatus.CREATED) {
                throw IllegalStateException("支付尝试 ${attempt.id} 已提交或已终结，不重复调用渠道")
            }

            val now = LocalDateTime.now(clock)
            val submitted = payment.freezeAttemptSubmission(attempt.id, now)
            val channelResult = try {
                Mediator.capabilities.call(
                    StartChannelPayment.Request(
                        paymentAttemptId = submitted.id.toString(),
                        channelId = submitted.channelId,
                        requestIdentity = submitted.requestIdentity,
                        amount = payment.amount,
                        currency = payment.currency,
                    ),
                )
            } catch (error: RuntimeException) {
                log.warn("支付渠道提交调用失败：paymentId={}, attemptId={}", payment.id, submitted.id, error)
                null
            }
            val outcome = channelResult?.outcome?.name ?: "RESULT_UNKNOWN"
            channelResult?.diagnosticSummary?.takeIf { it.isNotBlank() }?.let {
                log.warn(
                    "支付渠道提交原始诊断仅记录日志：paymentId={}, attemptId={}, diagnostic={}",
                    payment.id,
                    submitted.id,
                    it,
                )
            }
            val safeDiagnostic = safeDiagnostic(outcome, channelResult?.failureCode)
            payment.recordAttemptSubmission(
                paymentAttemptId = submitted.id,
                submissionIdentity = requireNotNull(submitted.submissionIdentity),
                submittedAt = now,
                outcome = outcome,
                channelReference = channelResult?.channelReference,
                diagnosticSummary = safeDiagnostic,
            )
            return response(
                payment,
                submitted,
                operationSupport.accept(
                    merchantId = payment.merchantId,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = idempotencyKey,
                    canonicalRequestHash = requestHash,
                    resourceType = "PaymentAttempt",
                    resourceId = submitted.id.toString(),
                    resourceUrl = "/api/payments/${payment.id}",
                ),
            )
        }

        private fun response(
            payment: com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment,
            attempt: com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttempt,
            receipt: OperationReceipt,
        ) = Response(
            paymentId = payment.id.toString(),
            paymentAttemptId = attempt.id.toString(),
            paymentStatus = payment.status.name,
            attemptStatus = attempt.status.name,
            submissionIdentity = attempt.submissionIdentity,
            channelId = attempt.channelId,
            interactionInformation = attempt.interactionInformation.orEmpty(),
            submissionOutcome = attempt.paymentSubmissionReceipts.lastOrNull()?.outcome,
            channelReference = attempt.paymentSubmissionReceipts.lastOrNull()?.channelReference,
            diagnosticSummary = attempt.paymentSubmissionReceipts.lastOrNull()?.diagnosticSummary,
            receipt = receipt,
        )
    }

    data class Request(val paymentId: PaymentId, val paymentAttemptId: PaymentAttemptId, val idempotencyKey: String) : Command<Response>

    data class Response(
        val paymentId: String,
        val paymentAttemptId: String,
        val paymentStatus: String,
        val attemptStatus: String,
        val submissionIdentity: String?,
        val channelId: String,
        val interactionInformation: String,
        val submissionOutcome: String?,
        val channelReference: String?,
        val diagnosticSummary: String?,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "SubmitPaymentAttempt"
    private val log = LoggerFactory.getLogger(SubmitPaymentAttemptCmd::class.java)

    private fun safeDiagnostic(outcome: String, failureCode: String?): String = when (outcome) {
        "ACCEPTED" -> "支付渠道已受理请求"
        "REJECTED" -> if (failureCode?.trim()?.uppercase() == "UNSUPPORTED_CHANNEL") {
            "支付渠道不支持当前请求"
        } else {
            "支付渠道拒绝了提交请求"
        }
        else -> "支付渠道提交结果未知，保留原提交身份等待收敛"
    }
}
