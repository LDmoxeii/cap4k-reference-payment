package com.only4.cap4k.reference.payment.application.commands.payment.review

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.payment.order.SerializeMerchantOrderSuccess
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.MerchantChannelConfigurationId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentReviewException
import com.only4.cap4k.reference.payment.domain.aggregates.payment.adjudicateReview
import com.only4.cap4k.reference.payment.domain.aggregates.payment.currentReviewEligibility
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewDecisionType
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewEligibilityImpact
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(tag = "command", name = "AdjudicatePaymentReview", packageName = "payment.review", description = "Append an authorized decision to one payment review without deleting evidence", aggregates = ["Payment"], family = "command")
object AdjudicatePaymentReviewCmd {
    @Service
    class Handler(
        private val referencePolicy: ReferencePolicyService,
        private val operationSupport: OperationSupport,
    ) : CommandHandler<Request, Response> {
        /**
         * 编排顺序必须保持清晰：先解析并验证授权，再在需要接受迟到成功时抢占商户订单成功身份、
         * 重建当时应冻结的手续费规则，最后把显式 code 的领域裁决结果投影为应用响应。
         * 任何一步失败都由同一 UoW 回滚，不留下部分 decision、无手续费的成功或错误通知意图。
         */
        override fun handle(command: Request): Response {
            val idempotencyKey = command.idempotencyKey.trim()
            require(idempotencyKey.isNotBlank()) { "支付复核幂等键不能为空" }
            val reviewId = command.reviewId.trim()
            require(reviewId.isNotBlank()) { "复核标识不能为空" }
            val decisionIdentity = command.decisionIdentity.trim()
            require(decisionIdentity.isNotBlank()) { "裁决身份不能为空" }
            val actorId = command.operatorIdentity.trim()
            require(actorId.isNotBlank()) { "操作员身份不能为空" }
            val actorRole = command.operatorRole.trim().uppercase()
            require(actorRole == AUTHORIZED_ROLE) { "当前 reference actor 不能裁决支付复核" }
            val reason = command.reason.trim()
            require(reason.isNotBlank()) { "复核原因不能为空" }
            val evidence = command.evidence.trim()
            require(evidence.isNotBlank()) { "复核证据不能为空" }
            val payment = Mediator.repositories.findOne(SPayment.predicateById(command.paymentId))
                ?: throw PaymentNotFoundException(command.paymentId)
            command.merchantId?.trim()?.let { merchantId ->
                require(merchantId.isNotBlank() && merchantId == payment.merchantId) { "支付复核商户范围不一致" }
            }
            val decision = enumValueOrConflict<PaymentReviewDecisionType>(command.decision, "REVIEW_DECISION_NOT_ALLOWED")
            val eligibilityImpact = enumValueOrConflict<PaymentReviewEligibilityImpact>(command.eligibilityImpact, "REVIEW_DECISION_NOT_ALLOWED")
            val remediationReference = command.remediationReference?.trim()?.takeIf(String::isNotBlank)
            val requestHash = operationSupport.canonicalHash(
                payment.merchantId, payment.id.toString(), reviewId, decisionIdentity,
                decision.name, eligibilityImpact.name, reason, evidence, remediationReference,
                actorId, actorRole,
            )
            operationSupport.replayOrNull(payment.merchantId, COMMAND_TYPE, idempotencyKey, requestHash)?.let { operation ->
                return response(payment, operation.resourceId, operationSupport.receipt(operation, replay = true))
            }

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
                Mediator.repositories.findOne(
                    SMerchantChannelConfiguration.predicateById(MerchantChannelConfigurationId.parse(attempt.channelConfigurationId))
                ) ?: throw PaymentConflictException("REVIEW_DECISION_NOT_ALLOWED", "成功证据引用的渠道配置不存在")
                referencePolicy.settlementFeeRule(payment.currency)
            } else null

            val outcome = try {
                payment.adjudicateReview(
                    reviewIdentity = reviewId,
                    decisionIdentity = decisionIdentity,
                    decision = decision,
                    operatorIdentity = actorId,
                    operatorRole = actorRole,
                    authorized = true,
                    reason = reason,
                    evidence = evidence,
                    decidedAt = LocalDateTime.ofInstant(command.decidedAt, ZoneOffset.UTC),
                    eligibilityImpact = eligibilityImpact,
                    remediationReference = remediationReference,
                    settlementFeeRule = feeRule,
                )
            } catch (error: PaymentReviewException) {
                throw PaymentConflictException(error.code, requireNotNull(error.message), error.details)
            }
            val review = payment.reviewCases.first { it.reviewIdentity == reviewId || it.id.toString() == reviewId }
            val savedDecision = review.paymentReviewDecisions.first { it.decisionIdentity == decisionIdentity }
            val receipt = operationSupport.accept(
                merchantId = payment.merchantId,
                commandType = COMMAND_TYPE,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = requestHash,
                resourceType = "PaymentReviewDecision",
                resourceId = savedDecision.id.toString(),
                resourceUrl = "/api/payments/${payment.id}",
            )
            return response(payment, savedDecision.id.toString(), receipt)
        }

        private fun response(
            payment: com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment,
            decisionId: String,
            receipt: OperationReceipt,
        ): Response {
            val (review, decision) = payment.reviewCases.asSequence()
                .flatMap { review -> review.paymentReviewDecisions.asSequence().map { review to it } }
                .firstOrNull { (_, decision) -> decision.id.toString() == decisionId }
                ?: throw IllegalStateException("Operation ${receipt.operationId} 引用的支付复核裁决不存在")
            return Response(
                paymentStatus = payment.status.name,
                reviewStatus = review.status.name,
                decisionCount = review.paymentReviewDecisions.size,
                settlementEligible = payment.status == PaymentStatus.SUCCEEDED &&
                    payment.currentReviewEligibility().settlementEligible,
                notificationIntentState = payment.merchantSuccessNotificationIntentState?.name,
                decisionId = decisionId,
                decisionIdentity = decision.decisionIdentity,
                decision = decision.decision.name,
                actorId = decision.operatorIdentity,
                reason = decision.reason,
                evidence = decision.evidence,
                decidedAt = decision.decidedAt.toInstant(ZoneOffset.UTC),
                receipt = receipt,
            )
        }
    }

    data class Request(
        /**
         * 支付标识
         */
        val paymentId: PaymentId,
        /**
         * 复核标识
         */
        val reviewId: String,
        /**
         * 决策身份
         */
        val decisionIdentity: String,
        val idempotencyKey: String,
        /** Public requests supply this explicitly; internal ManualReview delegation derives it from Payment. */
        val merchantId: String? = null,
        /**
         * 决策
         */
        val decision: String,
        /**
         * 操作员身份
         */
        val operatorIdentity: String,
        /**
         * 操作员角色
         */
        val operatorRole: String,
        /**
         * 原因
         */
        val reason: String,
        /**
         * 证据
         */
        val evidence: String,
        /**
         * 决策时间
         */
        val decidedAt: Instant,
        /**
         * 资格影响
         */
        val eligibilityImpact: String,
        /**
         * 补救引用
         */
        val remediationReference: String?,
    ) : Command<Response>

    data class Response(
        /**
         * 支付状态
         */
        val paymentStatus: String,
        /**
         * 复核状态
         */
        val reviewStatus: String,
        /**
         * 决策数量
         */
        val decisionCount: Int,
        /**
         * 是否符合结算条件
         */
        val settlementEligible: Boolean,
        /**
         * 通知意图状态
         */
        val notificationIntentState: String?,
        val decisionId: String,
        val decisionIdentity: String,
        val decision: String,
        val actorId: String,
        val reason: String,
        val evidence: String,
        val decidedAt: Instant,
        val receipt: OperationReceipt,
    )

    private inline fun <reified E : Enum<E>> enumValueOrConflict(value: String, code: String): E =
        runCatching { enumValueOf<E>(value.trim().uppercase()) }
            .getOrElse { throw PaymentConflictException(code, "不支持的复核参数值：$value", mapOf("value" to value)) }

    private const val AUTHORIZED_ROLE = "PAYMENT_REVIEW_OPERATOR"
    private const val COMMAND_TYPE = "AdjudicatePaymentReview"
}
