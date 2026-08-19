package com.only4.cap4k.reference.payment.application.commands.refund.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.refund.channel.VerifyRefundResult
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.convertRefundReservationToSuccess
import com.only4.cap4k.reference.payment.domain.aggregates.payment.releaseRefundReservation
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.refund.values.RefundResultRecordingOutcome
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ConfirmRefundResult",
    packageName = "refund.result",
    description = "Record, verify, deduplicate, and adjudicate a channel refund result",
    aggregates = ["Payment", "Refund"],
    family = "command",
)
object ConfirmRefundResultCmd {

    @Service
    class Handler(private val clock: Clock) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val payload = listOf(
                command.channelId,
                command.notificationId,
                command.refundId,
                command.refundAttemptId,
                command.channelRefundId,
                command.amount.toPlainString(),
                command.currency,
                command.result,
                command.occurredAt.toString(),
            ).joinToString("|")
            val verification = Mediator.capabilities.call(
                VerifyRefundResult.Request(
                    channelId = command.channelId,
                    notificationId = command.notificationId,
                    payload = payload,
                    verificationMaterial = command.verificationMaterial,
                )
            )
            val refund = Mediator.repositories.findOne(
                SRefund.predicateById(RefundId.parse(command.refundId))
            ) ?: throw RefundNotFoundException(command.refundId)
            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(PaymentId.parse(refund.paymentId.toString()))
            ) ?: throw PaymentNotFoundException(refund.paymentId.toString())
            val outcome = refund.recordChannelResult(
                attemptId = RefundAttemptId.parse(command.refundAttemptId),
                channelId = command.channelId,
                notificationId = command.notificationId,
                channelRefundId = command.channelRefundId,
                amount = command.amount,
                currency = command.currency,
                result = command.result,
                occurredAt = LocalDateTime.ofInstant(command.occurredAt, ZoneOffset.UTC),
                receivedAt = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC),
                verified = verification.verified,
                verificationSummary = verification.verificationSummary,
            )
            if (outcome.reservationReleasedNow) {
                payment.releaseRefundReservation(refund.amount)
            }
            if (outcome.reservationConvertedToSuccessNow) {
                payment.convertRefundReservationToSuccess(refund.amount)
            }
            return Response(outcome)
        }
    }

    data class Request(
        val channelId: String,
        val notificationId: String,
        val refundId: String,
        val refundAttemptId: String,
        val channelRefundId: String,
        val amount: BigDecimal,
        val currency: String,
        val result: String,
        val occurredAt: Instant,
        val verificationMaterial: String,
    ) : Command<Response>

    data class Response(val outcome: RefundResultRecordingOutcome)
}
