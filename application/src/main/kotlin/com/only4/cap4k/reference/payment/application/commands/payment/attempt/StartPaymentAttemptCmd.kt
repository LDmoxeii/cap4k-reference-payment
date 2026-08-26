package com.only4.cap4k.reference.payment.application.commands.payment.attempt

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.application.capabilities.payment.gateway.StartChannelPayment
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.currentReviewEligibility
import com.only4.cap4k.reference.payment.domain.aggregates.payment.rejectAttemptStart
import com.only4.cap4k.reference.payment.domain.aggregates.payment.startAttempt
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "StartPaymentAttempt",
    packageName = "payment.attempt",
    description = "Select an eligible merchant channel and start a durable payment attempt",
    aggregates = ["Payment", "MerchantChannelConfiguration"],
    family = "command"
)
object StartPaymentAttemptCmd {

    @Service
    class Handler(
        private val clock: Clock,
    ) : CommandHandler<Request, Response> {

        /**
         * 每次发起前重新检查 expiresAt、Payment 状态和 review eligibility，再选择当前合格配置并创建 attempt。
         * Gateway ACCEPTED 只表示渠道受理；异常或拒绝必须留下可查询 attempt 诊断，不能伪造支付成功。
         */
        override fun handle(command: Request): Response {
            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(PaymentId.parse(command.paymentId))
            ) ?: throw PaymentNotFoundException(command.paymentId)

            val now = LocalDateTime.now(clock)
            if (!now.isBefore(payment.expiresAt)) {
                throw PaymentConflictException("PAYMENT_EXPIRED", "支付单 ${payment.id} 已过期")
            }
            if (!payment.currentReviewEligibility().settlementEligible) {
                throw PaymentConflictException("PAYMENT_REVIEW_REQUIRED", "支付单 ${payment.id} 仍有未解决的阻断复核")
            }
            payment.attempts.firstOrNull { it.status == PaymentAttemptStatus.PROCESSING }?.let { current ->
                return Response(
                    paymentAttemptId = current.id.toString(),
                    channelId = current.channelId,
                    requestIdentity = current.requestIdentity,
                    paymentStatus = payment.status.name,
                    attemptStatus = current.status.name,
                    diagnosticSummary = "复用了当前正在处理的支付尝试",
                )
            }

            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq payment.merchantId) and
                        (schema.currency eq payment.currency) and
                        (schema.paymentMethod eq payment.paymentMethod) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE) and
                        (schema.minimumAmount le payment.amount) and
                        (schema.maximumAmount ge payment.amount)
                }
            ) ?: throw NoEligibleChannelException("payment ${payment.id}")

            val requestIdentity = "${payment.id}:${payment.attemptCount + 1}"
            val snapshot = listOf(
                "channelId=${configuration.channelId}",
                "currency=${configuration.currency}",
                "paymentMethod=${configuration.paymentMethod}",
                "minimumAmount=${configuration.minimumAmount}",
                "maximumAmount=${configuration.maximumAmount}",
                "routingPriority=${configuration.routingPriority}",
            ).joinToString(";")
            val attempt = payment.startAttempt(
                channelId = configuration.channelId,
                channelConfigurationId = configuration.id.toString(),
                channelConfigurationSnapshot = snapshot,
                requestIdentity = requestIdentity,
                initiatedAt = now,
            )
            val gateway = try {
                Mediator.capabilities.call(
                    StartChannelPayment.Request(
                        paymentAttemptId = attempt.id.toString(),
                        channelId = attempt.channelId,
                        requestIdentity = attempt.requestIdentity,
                        amount = payment.amount,
                        currency = payment.currency,
                    )
                )
            } catch (error: RuntimeException) {
                log.warn("支付渠道调用失败：paymentId={}, attemptId={}", payment.id, attempt.id, error)
                val diagnostic = "支付渠道调用失败，请稍后重试"
                payment.rejectAttemptStart(
                    paymentAttemptId = attempt.id,
                    failureCode = "CHANNEL_GATEWAY_ERROR",
                    diagnosticSummary = diagnostic,
                )
                return Response(
                    paymentAttemptId = attempt.id.toString(),
                    channelId = attempt.channelId,
                    requestIdentity = attempt.requestIdentity,
                    paymentStatus = payment.status.name,
                    attemptStatus = attempt.status.name,
                    diagnosticSummary = diagnostic,
                )
            }
            val safeDiagnostic = paymentGatewaySummary(gateway.accepted, gateway.failureCode)
            if (!gateway.accepted) {
                gateway.diagnosticSummary?.takeIf { it.isNotBlank() }?.let {
                    log.warn("支付渠道拒绝原始诊断仅记录日志：paymentId={}, attemptId={}, diagnostic={}", payment.id, attempt.id, it)
                }
                payment.rejectAttemptStart(
                    paymentAttemptId = attempt.id,
                    failureCode = gateway.failureCode ?: "CHANNEL_REJECTED",
                    diagnosticSummary = safeDiagnostic,
                )
            }
            return Response(
                paymentAttemptId = attempt.id.toString(),
                channelId = attempt.channelId,
                requestIdentity = attempt.requestIdentity,
                paymentStatus = payment.status.name,
                attemptStatus = attempt.status.name,
                diagnosticSummary = safeDiagnostic,
            )
        }
    }

    private fun paymentGatewaySummary(accepted: Boolean, failureCode: String?): String = when {
        accepted -> "支付渠道已受理请求"
        failureCode?.trim()?.uppercase() == "UNSUPPORTED_CHANNEL" -> "支付渠道不支持当前请求"
        else -> "支付渠道拒绝了请求"
    }

    private val log = LoggerFactory.getLogger(StartPaymentAttemptCmd::class.java)

    data class Request(
        /**
         * 支付标识
         */
        val paymentId: String
    ) : Command<Response>

    data class Response(
        /**
         * 支付尝试标识
         */
        val paymentAttemptId: String,
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 请求身份
         */
        val requestIdentity: String,
        /**
         * 支付状态
         */
        val paymentStatus: String,
        /**
         * 尝试状态
         */
        val attemptStatus: String,
        /**
         * 诊断摘要
         */
        val diagnosticSummary: String?
    )
}
