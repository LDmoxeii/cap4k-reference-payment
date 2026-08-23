package com.only4.cap4k.reference.payment.application.commands.payment.review

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.payment.order.SerializeMerchantOrderSuccess
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.MerchantChannelConfigurationId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentReviewException
import com.only4.cap4k.reference.payment.domain.aggregates.payment.SettlementFeeRule
import com.only4.cap4k.reference.payment.domain.aggregates.payment.adjudicateReview
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewDecisionType
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewEligibilityImpact
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.values.Money
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(tag = "command", name = "AdjudicatePaymentReview", packageName = "payment.review", description = "Append an authorized decision to one payment review without deleting evidence", aggregates = ["Payment"], family = "command")
object AdjudicatePaymentReviewCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        /**
         * 编排顺序必须保持清晰：先解析并验证授权，再在需要接受迟到成功时抢占商户订单成功身份、
         * 重建当时应冻结的手续费规则，最后把显式 code 的领域裁决结果投影为应用响应。
         * 任何一步失败都由同一 UoW 回滚，不留下部分 decision、无手续费的成功或错误通知意图。
         */
        override fun handle(command: Request): Response {
            val payment = Mediator.repositories.findOne(SPayment.predicateById(PaymentId.parse(command.paymentId)))
                ?: throw PaymentNotFoundException(command.paymentId)
            val decision = enumValueOrConflict<PaymentReviewDecisionType>(command.decision, "REVIEW_DECISION_NOT_ALLOWED")
            val eligibilityImpact = enumValueOrConflict<PaymentReviewEligibilityImpact>(command.eligibilityImpact, "REVIEW_DECISION_NOT_ALLOWED")
            val authorized = command.operatorRole.trim().uppercase() == AUTHORIZED_ROLE &&
                command.authorizationMaterial.trim() == AUTHORIZED_MATERIAL

            val feeRule = if (decision == PaymentReviewDecisionType.ACCEPT_LATE_SUCCESS) {
                Mediator.capabilities.call(SerializeMerchantOrderSuccess.Request(payment.merchantId))
                Mediator.repositories.findOne(
                    SPayment.predicate { schema ->
                        (schema.merchantId eq payment.merchantId) and
                            (schema.merchantOrderNumber eq payment.merchantOrderNumber) and
                            (schema.status eq PaymentStatus.SUCCEEDED)
                    }
                )?.takeIf { it.id != payment.id }?.let {
                    throw PaymentConflictException("ORDER_ALREADY_PAID", "商户订单 ${payment.merchantOrderNumber} 已经存在成功支付")
                }
                val attempt = payment.attempts.firstOrNull { it.status == PaymentAttemptStatus.SUCCEEDED }
                    ?: throw PaymentConflictException("REVIEW_DECISION_NOT_ALLOWED", "复核中没有可信的成功证据")
                val configuration = Mediator.repositories.findOne(
                    SMerchantChannelConfiguration.predicateById(MerchantChannelConfigurationId.parse(attempt.channelConfigurationId))
                ) ?: throw PaymentConflictException("REVIEW_DECISION_NOT_ALLOWED", "成功证据引用的渠道配置不存在")
                SettlementFeeRule(
                    configurationId = configuration.id.toString(),
                    basisPoints = configuration.settlementFeeBasisPoints,
                    fixedFeeAmount = configuration.settlementFixedFeeAmount,
                    roundingMode = runCatching { RoundingMode.valueOf(configuration.settlementFeeRoundingMode.trim().uppercase()) }
                        .getOrElse { throw PaymentConflictException("REVIEW_DECISION_NOT_ALLOWED", "不支持该渠道配置中的结算手续费舍入模式") },
                    currencyPrecision = Money.fractionDigits(payment.currency),
                )
            } else null

            val outcome = try {
                payment.adjudicateReview(
                    reviewIdentity = command.reviewId,
                    decisionIdentity = command.decisionIdentity,
                    decision = decision,
                    operatorIdentity = command.operatorIdentity,
                    operatorRole = command.operatorRole,
                    authorized = authorized,
                    reason = command.reason,
                    evidence = command.evidence,
                    decidedAt = LocalDateTime.ofInstant(command.decidedAt, ZoneOffset.UTC),
                    eligibilityImpact = eligibilityImpact,
                    remediationReference = command.remediationReference,
                    settlementFeeRule = feeRule,
                )
            } catch (error: PaymentReviewException) {
                throw PaymentConflictException(error.code, requireNotNull(error.message), error.details)
            }
            return Response(
                paymentStatus = outcome.paymentStatus.name,
                reviewStatus = outcome.reviewStatus.name,
                decisionCount = outcome.decisionCount,
                settlementEligible = outcome.settlementEligible,
                notificationIntentState = outcome.notificationIntentState?.name,
            )
        }
    }

    data class Request(
        val paymentId: String,
        val reviewId: String,
        val decisionIdentity: String,
        val decision: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val authorizationMaterial: String,
        val reason: String,
        val evidence: String,
        val decidedAt: Instant,
        val eligibilityImpact: String,
        val remediationReference: String?,
    ) : Command<Response>

    data class Response(
        val paymentStatus: String,
        val reviewStatus: String,
        val decisionCount: Int,
        val settlementEligible: Boolean,
        val notificationIntentState: String?,
    )

    private inline fun <reified E : Enum<E>> enumValueOrConflict(value: String, code: String): E =
        runCatching { enumValueOf<E>(value.trim().uppercase()) }
            .getOrElse { throw PaymentConflictException(code, "不支持的复核参数值：$value", mapOf("value" to value)) }

    private const val AUTHORIZED_ROLE = "PAYMENT_REVIEW_OPERATOR"
    private const val AUTHORIZED_MATERIAL = "AUTHORIZED"
}
