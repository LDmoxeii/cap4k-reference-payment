package com.only4.cap4k.reference.payment.application.commands.payment.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.payment.channel.VerifyPaymentResult
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ConfirmPaymentResult",
    packageName = "payment.result",
    description = "Record, verify, deduplicate, and adjudicate a channel payment result",
    aggregates = ["Payment"],
    family = "command"
)
object ConfirmPaymentResultCmd {

    @Service
    class Handler(
        private val capabilities: CapabilitySupervisor,
        private val clock: Clock,
    ) : CommandHandler<Request, Response> {

        override fun handle(command: Request): Response {
            val payload = listOf(
                command.channelId,
                command.notificationId,
                command.paymentId,
                command.paymentAttemptId,
                command.channelTransactionId,
                command.amount.toPlainString(),
                command.currency,
                command.result,
                command.occurredAt.toString(),
            ).joinToString("|")
            val verification = capabilities.call(
                VerifyPaymentResult.Request(
                    channelId = command.channelId,
                    notificationId = command.notificationId,
                    payload = payload,
                    verificationMaterial = command.verificationMaterial,
                )
            )
            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(PaymentId.parse(command.paymentId))
            ) ?: throw PaymentNotFoundException(command.paymentId)
            val decision = payment.recordChannelResult(
                paymentAttemptId = PaymentAttemptId.parse(command.paymentAttemptId),
                channelId = command.channelId,
                notificationId = command.notificationId,
                channelTransactionId = command.channelTransactionId,
                amount = command.amount,
                currency = command.currency.uppercase(),
                result = command.result,
                occurredAt = LocalDateTime.ofInstant(command.occurredAt, ZoneOffset.UTC),
                receivedAt = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC),
                verified = verification.verified,
                verificationSummary = verification.verificationSummary,
            )
            return Response(outcome = decision)
        }
    }

    data class Request(
        val channelId: String,
        val notificationId: String,
        val paymentId: String,
        val paymentAttemptId: String,
        val channelTransactionId: String,
        val amount: BigDecimal,
        val currency: String,
        val result: String,
        val occurredAt: Instant,
        val verificationMaterial: String
    ) : Command<Response>

    data class Response(
        val outcome: ChannelResultRecordingOutcome
    )
}
