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
                    throw PaymentConflictException("ORDER_ALREADY_PAID", "merchant order ${payment.merchantOrderNumber} already has a successful payment")
                }
                val attempt = payment.attempts.firstOrNull { it.status == PaymentAttemptStatus.SUCCEEDED }
                    ?: throw PaymentConflictException("REVIEW_DECISION_NOT_ALLOWED", "review has no trustworthy success evidence")
                val configuration = Mediator.repositories.findOne(
                    SMerchantChannelConfiguration.predicateById(MerchantChannelConfigurationId.parse(attempt.channelConfigurationId))
                ) ?: throw PaymentConflictException("REVIEW_DECISION_NOT_ALLOWED", "success evidence references a missing channel configuration")
                SettlementFeeRule(
                    configurationId = configuration.id.toString(),
                    basisPoints = configuration.settlementFeeBasisPoints,
                    fixedFeeAmount = configuration.settlementFixedFeeAmount,
                    roundingMode = runCatching { RoundingMode.valueOf(configuration.settlementFeeRoundingMode.trim().uppercase()) }
                        .getOrElse { throw PaymentConflictException("REVIEW_DECISION_NOT_ALLOWED", "unsupported settlement fee rounding mode") },
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
            } catch (error: IllegalStateException) {
                val code = error.message?.takeIf { it.startsWith("REVIEW_") } ?: "REVIEW_DECISION_NOT_ALLOWED"
                throw PaymentConflictException(code, error.message ?: code)
            } catch (error: IllegalArgumentException) {
                val code = error.message?.takeIf { it.startsWith("REVIEW_") } ?: "REVIEW_DECISION_NOT_ALLOWED"
                throw PaymentConflictException(code, error.message ?: code)
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
            .getOrElse { throw PaymentConflictException(code, "unsupported value: $value") }

    private const val AUTHORIZED_ROLE = "PAYMENT_REVIEW_OPERATOR"
    private const val AUTHORIZED_MATERIAL = "AUTHORIZED"
}
