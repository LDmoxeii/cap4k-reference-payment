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
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.rejectAttemptStart
import com.only4.cap4k.reference.payment.domain.aggregates.payment.startAttempt
import java.time.Clock
import java.time.LocalDateTime
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

        override fun handle(command: Request): Response {
            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(PaymentId.parse(command.paymentId))
            ) ?: throw PaymentNotFoundException(command.paymentId)

            payment.attempts.firstOrNull { it.status == PaymentAttemptStatus.PROCESSING }?.let { current ->
                return Response(
                    paymentAttemptId = current.id.toString(),
                    channelId = current.channelId,
                    requestIdentity = current.requestIdentity,
                    paymentStatus = payment.status.name,
                    attemptStatus = current.status.name,
                    diagnosticSummary = "reused the active payment attempt",
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

            val now = LocalDateTime.now(clock)
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
                val diagnostic = "channel gateway failed: ${error.message ?: error::class.simpleName}"
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
            if (!gateway.accepted) {
                payment.rejectAttemptStart(
                    paymentAttemptId = attempt.id,
                    failureCode = gateway.failureCode ?: "CHANNEL_REJECTED",
                    diagnosticSummary = gateway.diagnosticSummary,
                )
            }
            return Response(
                paymentAttemptId = attempt.id.toString(),
                channelId = attempt.channelId,
                requestIdentity = attempt.requestIdentity,
                paymentStatus = payment.status.name,
                attemptStatus = attempt.status.name,
                diagnosticSummary = gateway.diagnosticSummary,
            )
        }
    }

    data class Request(
        val paymentId: String
    ) : Command<Response>

    data class Response(
        val paymentAttemptId: String,
        val channelId: String,
        val requestIdentity: String,
        val paymentStatus: String,
        val attemptStatus: String,
        val diagnosticSummary: String?
    )
}
