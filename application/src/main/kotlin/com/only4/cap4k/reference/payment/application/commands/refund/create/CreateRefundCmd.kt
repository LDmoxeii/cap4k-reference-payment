package com.only4.cap4k.reference.payment.application.commands.refund.create

import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.refund.gateway.StartChannelRefund
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundConflictException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.reserveRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.releaseRefundReservation
import com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.factory.RefundFactory
import com.only4.cap4k.reference.payment.domain.aggregates.refund.markChannelAccepted
import com.only4.cap4k.reference.payment.domain.aggregates.refund.rejectAttemptStart
import com.only4.cap4k.reference.payment.domain.aggregates.refund.startAttempt
import com.only4.cap4k.reference.payment.domain.values.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "CreateRefund",
    packageName = "refund.create",
    description = "Reserve payment refund budget, create a refund, and submit one channel attempt",
    aggregates = ["Payment", "Refund", "MerchantChannelConfiguration"],
    family = "command",
)
object CreateRefundCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {
        /**
         * 创建退款先按 merchantRefundNumber 做幂等判定，再装载成功 Payment、检查期限与渠道资格并占用退款预算。
         * Refund/Attempt 创建、Gateway 结果和 Payment 预算释放或保留都在同一 UoW 收敛，任一步失败不得半提交。
         */
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val merchantRefundNumber = command.merchantRefundNumber.trim()
            require(merchantId.isNotBlank()) { "商户身份不能为空" }
            require(merchantRefundNumber.isNotBlank()) { "商户退款号不能为空" }
            val money = Money.of(command.amount, command.currency)
            val requestedAt = LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC)

            val existing = Mediator.repositories.findOne(
                SRefund.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.merchantRefundNumber eq merchantRefundNumber)
                }
            )
            if (existing != null) {
                val sameRequest = existing.paymentId == command.paymentId &&
                    existing.amount.compareTo(money.amount) == 0 &&
                    existing.currency == money.currency
                if (!sameRequest) {
                    throw RefundConflictException(
                        code = "REFUND_IDEMPOTENCY_CONFLICT",
                        message = "商户退款号已绑定到内容不同的退款申请",
                    )
                }
                val attempt = existing.attempts.firstOrNull()
                    ?: throw RefundConflictException("REFUND_WITHOUT_ATTEMPT", "退款单 ${existing.id} 没有退款尝试")
                return Response(
                    refundId = existing.id,
                    refundAttemptId = attempt.id.toString(),
                    status = existing.status.name,
                    requestIdentity = attempt.requestIdentity,
                    idempotentReplay = true,
                    diagnosticSummary = "复用了已有退款申请",
                )
            }

            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(command.paymentId)
            ) ?: throw PaymentNotFoundException(command.paymentId)
            require(payment.merchantId == merchantId) { "支付单不属于商户 $merchantId" }
            require(payment.currency == money.currency) {
                "支付单 ${payment.id} 的币种 ${payment.currency} 与退款币种 ${money.currency} 不一致"
            }
            require(payment.status == PaymentStatus.SUCCEEDED) { "支付单 ${payment.id} 当前状态为 ${payment.status}，不能退款" }
            val succeededAt = requireNotNull(payment.succeededAt) { "支付单 ${payment.id} 缺少成功时间" }
            require(!requestedAt.isBefore(succeededAt)) { "退款申请时间不能早于支付成功时间" }

            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.currency eq money.currency) and
                        (schema.paymentMethod eq payment.paymentMethod) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE) and
                        (schema.minimumAmount le money.amount) and
                        (schema.maximumAmount ge money.amount)
                }
            ) ?: throw NoEligibleChannelException("商户 $merchantId 的 ${money.currency}/${payment.paymentMethod} 退款")

            val deadline = succeededAt.plusDays(configuration.refundWindowDays.toLong())
            require(!requestedAt.isAfter(deadline)) { "退款期限已于 $deadline 结束" }
            payment.reserveRefund(money.amount)

            val snapshot = listOf(
                "channelId=${configuration.channelId}",
                "currency=${configuration.currency}",
                "paymentMethod=${configuration.paymentMethod}",
                "minimumAmount=${configuration.minimumAmount}",
                "maximumAmount=${configuration.maximumAmount}",
                "refundWindowDays=${configuration.refundWindowDays}",
                "refundResultReviewAfterMinutes=${configuration.refundResultReviewAfterMinutes}",
            ).joinToString(";")
            val requestIdentity = "${payment.id}:$merchantRefundNumber:1"
            val reviewAfterAt = requestedAt.plusMinutes(configuration.refundResultReviewAfterMinutes.toLong())
            val refund = Mediator.factories.create<RefundFactory.Payload, Refund>(
                RefundFactory.Payload(
                    paymentId = payment.id,
                    merchantId = merchantId,
                    merchantRefundNumber = merchantRefundNumber,
                    amount = money.amount,
                    currency = money.currency,
                    paymentMethod = payment.paymentMethod,
                    status = RefundStatus.PROCESSING,
                    requestedAt = requestedAt,
                    refundDeadlineAt = deadline,
                    channelAcceptedAt = null,
                    finalizedAt = null,
                    reviewRequiredAt = null,
                    channelId = configuration.channelId,
                    channelConfigurationId = configuration.id.toString(),
                    channelConfigurationSnapshot = snapshot,
                    requestIdentity = requestIdentity,
                    channelRefundId = null,
                    lastNotificationIdentity = null,
                    lastNotificationReceivedAt = null,
                    lastRejectionSummary = null,
                    lastConflictSummary = null,
                    attempts = emptyList(),
                )
            )
            val attempt = refund.startAttempt(
                now = requestedAt,
                channelId = configuration.channelId,
                configurationId = configuration.id.toString(),
                snapshot = snapshot,
                requestIdentity = requestIdentity,
                reviewAfterAt = reviewAfterAt,
            )
            val gateway = try {
                Mediator.capabilities.call(
                    StartChannelRefund.Request(
                        refundAttemptId = attempt.id.toString(),
                        channelId = attempt.channelId,
                        requestIdentity = attempt.requestIdentity,
                        amount = refund.amount,
                        currency = refund.currency,
                    )
                )
            } catch (error: RuntimeException) {
                log.warn("退款渠道调用失败：refundId={}, attemptId={}", refund.id, attempt.id, error)
                val diagnostic = "退款渠道调用失败，请稍后重试"
                refund.rejectAttemptStart(attempt.id, "CHANNEL_GATEWAY_ERROR", diagnostic)
                payment.releaseRefundReservation(refund.amount)
                return Response(
                    refundId = refund.id,
                    refundAttemptId = attempt.id.toString(),
                    status = refund.status.name,
                    requestIdentity = attempt.requestIdentity,
                    idempotentReplay = false,
                    diagnosticSummary = diagnostic,
                )
            }
            val safeDiagnostic = refundGatewaySummary(gateway.accepted, gateway.failureCode)
            if (!gateway.accepted || gateway.channelRefundId.isNullOrBlank()) {
                gateway.diagnosticSummary?.takeIf { it.isNotBlank() }?.let {
                    log.warn("退款渠道拒绝原始诊断仅记录日志：refundId={}, attemptId={}, diagnostic={}", refund.id, attempt.id, it)
                }
                refund.rejectAttemptStart(
                    attempt.id,
                    gateway.failureCode ?: "CHANNEL_REJECTED",
                    safeDiagnostic,
                )
                payment.releaseRefundReservation(refund.amount)
                return Response(
                    refundId = refund.id,
                    refundAttemptId = attempt.id.toString(),
                    status = refund.status.name,
                    requestIdentity = attempt.requestIdentity,
                    idempotentReplay = false,
                    diagnosticSummary = safeDiagnostic,
                )
            }
            refund.markChannelAccepted(attempt.id, gateway.channelRefundId, requestedAt)
            return Response(
                refundId = refund.id,
                refundAttemptId = attempt.id.toString(),
                status = refund.status.name,
                requestIdentity = attempt.requestIdentity,
                idempotentReplay = false,
                diagnosticSummary = safeDiagnostic,
            )
        }
    }

    private fun refundGatewaySummary(accepted: Boolean, failureCode: String?): String = when {
        accepted -> "退款渠道已受理请求"
        failureCode?.trim()?.uppercase() == "UNSUPPORTED_CHANNEL" -> "退款渠道不支持当前请求"
        else -> "退款渠道拒绝了请求"
    }

    private val log = LoggerFactory.getLogger(CreateRefundCmd::class.java)

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 商户退款单号
         */
        val merchantRefundNumber: String,
        /**
         * 支付标识
         */
        val paymentId: PaymentId,
        /**
         * 金额
         */
        val amount: BigDecimal,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 请求时间
         */
        val requestedAt: Instant,
    ) : Command<Response>

    data class Response(
        /**
         * 退款标识
         */
        val refundId: RefundId,
        /**
         * 退款尝试标识
         */
        val refundAttemptId: String,
        /**
         * 状态
         */
        val status: String,
        /**
         * 请求身份
         */
        val requestIdentity: String,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean,
        /**
         * 诊断摘要
         */
        val diagnosticSummary: String?,
    )
}
