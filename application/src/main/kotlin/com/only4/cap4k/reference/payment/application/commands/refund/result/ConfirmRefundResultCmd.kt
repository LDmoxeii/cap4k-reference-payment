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
        /** 先调用退款结果核验 Capability，再由 Refund 聚合追加 receipt；结果返回后同步转换或释放 Payment 预算。 */
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
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 通知标识
         */
        val notificationId: String,
        /**
         * 退款标识
         */
        val refundId: String,
        /**
         * 退款尝试标识
         */
        val refundAttemptId: String,
        /**
         * 渠道退款标识
         */
        val channelRefundId: String,
        /**
         * 金额
         */
        val amount: BigDecimal,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 结果
         */
        val result: String,
        /**
         * 发生时间
         */
        val occurredAt: Instant,
        /**
         * 核验材料
         */
        val verificationMaterial: String,
    ) : Command<Response>

    data class Response(val outcome: RefundResultRecordingOutcome)
}
