package com.only4.cap4k.reference.payment.application.commands.refund.create

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
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val merchantRefundNumber = command.merchantRefundNumber.trim()
            require(merchantId.isNotBlank()) { "merchantId must not be blank" }
            require(merchantRefundNumber.isNotBlank()) { "merchantRefundNumber must not be blank" }
            val money = Money.of(command.amount, command.currency)
            val requestedAt = LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC)

            val existing = Mediator.repositories.findOne(
                SRefund.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.merchantRefundNumber eq merchantRefundNumber)
                }
            )
            if (existing != null) {
                val sameRequest = existing.paymentId == PaymentId.parse(command.paymentId) &&
                    existing.amount.compareTo(money.amount) == 0 &&
                    existing.currency == money.currency
                if (!sameRequest) {
                    throw RefundConflictException(
                        code = "REFUND_IDEMPOTENCY_CONFLICT",
                        message = "merchant refund number is already bound to a different refund",
                    )
                }
                val attempt = existing.attempts.firstOrNull()
                    ?: throw RefundConflictException("REFUND_WITHOUT_ATTEMPT", "refund ${existing.id} has no attempt")
                return Response(
                    refundId = existing.id.toString(),
                    refundAttemptId = attempt.id.toString(),
                    status = existing.status.name,
                    requestIdentity = attempt.requestIdentity,
                    idempotentReplay = true,
                    diagnosticSummary = "replayed an existing refund request",
                )
            }

            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(PaymentId.parse(command.paymentId))
            ) ?: throw PaymentNotFoundException(command.paymentId)
            require(payment.merchantId == merchantId) { "payment does not belong to merchant $merchantId" }
            require(payment.currency == money.currency) {
                "payment ${payment.id} currency ${payment.currency} does not match refund currency ${money.currency}"
            }
            require(payment.status == PaymentStatus.SUCCEEDED) { "payment ${payment.id} is not refundable while ${payment.status}" }
            val succeededAt = requireNotNull(payment.succeededAt) { "payment ${payment.id} has no succeededAt" }
            require(!requestedAt.isBefore(succeededAt)) { "refund requestedAt must not precede payment success" }

            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.currency eq money.currency) and
                        (schema.paymentMethod eq payment.paymentMethod) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE) and
                        (schema.minimumAmount le money.amount) and
                        (schema.maximumAmount ge money.amount)
                }
            ) ?: throw NoEligibleChannelException("refund $merchantId and ${money.currency}/${payment.paymentMethod}")

            val deadline = succeededAt.plusDays(configuration.refundWindowDays.toLong())
            require(!requestedAt.isAfter(deadline)) { "refund window expired at $deadline" }
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
                val diagnostic = "channel gateway failed: ${error.message ?: error::class.simpleName}"
                refund.rejectAttemptStart(attempt.id, "CHANNEL_GATEWAY_ERROR", diagnostic)
                payment.releaseRefundReservation(refund.amount)
                return Response(
                    refundId = refund.id.toString(),
                    refundAttemptId = attempt.id.toString(),
                    status = refund.status.name,
                    requestIdentity = attempt.requestIdentity,
                    idempotentReplay = false,
                    diagnosticSummary = diagnostic,
                )
            }
            if (!gateway.accepted || gateway.channelRefundId.isNullOrBlank()) {
                refund.rejectAttemptStart(
                    attempt.id,
                    gateway.failureCode ?: "CHANNEL_REJECTED",
                    gateway.diagnosticSummary,
                )
                payment.releaseRefundReservation(refund.amount)
                return Response(
                    refundId = refund.id.toString(),
                    refundAttemptId = attempt.id.toString(),
                    status = refund.status.name,
                    requestIdentity = attempt.requestIdentity,
                    idempotentReplay = false,
                    diagnosticSummary = listOfNotNull(
                        gateway.failureCode ?: "CHANNEL_REJECTED",
                        gateway.diagnosticSummary,
                    ).joinToString(": "),
                )
            }
            refund.markChannelAccepted(attempt.id, gateway.channelRefundId, requestedAt)
            return Response(
                refundId = refund.id.toString(),
                refundAttemptId = attempt.id.toString(),
                status = refund.status.name,
                requestIdentity = attempt.requestIdentity,
                idempotentReplay = false,
                diagnosticSummary = gateway.diagnosticSummary,
            )
        }
    }

    data class Request(
        val merchantId: String,
        val merchantRefundNumber: String,
        val paymentId: String,
        val amount: BigDecimal,
        val currency: String,
        val requestedAt: Instant,
    ) : Command<Response>

    data class Response(
        val refundId: String,
        val refundAttemptId: String,
        val status: String,
        val requestIdentity: String,
        val idempotentReplay: Boolean,
        val diagnosticSummary: String?,
    )
}
