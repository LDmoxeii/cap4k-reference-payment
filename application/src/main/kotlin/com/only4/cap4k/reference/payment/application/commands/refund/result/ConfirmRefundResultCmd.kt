package com.only4.cap4k.reference.payment.application.commands.refund.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.refund.channel.VerifyRefundResult
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.application.manual_review.openRefundResultReview
import com.only4.cap4k.reference.payment.application.commands.merchant_notification.MerchantNotificationService
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.convertRefundReservationToSuccess
import com.only4.cap4k.reference.payment.domain.aggregates.payment.releaseRefundReservation
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.refund.values.RefundResultRecordingOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
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
    class Handler(
        private val clock: Clock,
        private val manualReviewSupport: ManualReviewSupport,
        private val notifications: MerchantNotificationService,
        private val operations: OperationSupport,
    ) : CommandHandler<Request, Response> {
        /** 先调用退款结果核验 Capability，再由 Refund 聚合追加 receipt；结果返回后同步转换或释放 Payment 预算。 */
        override fun handle(command: Request): Response {
            val payload = command.rawPayload?.takeIf { it.isNotBlank() } ?: listOf(
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
            val operationIdentity = operations.canonicalHash(
                command.channelId.trim(),
                command.notificationId.trim(),
                command.refundId.toString(),
                command.refundAttemptId.trim(),
                command.channelRefundId.trim(),
                command.amount.stripTrailingZeros().toPlainString(),
                command.currency.trim().uppercase(),
                command.result.trim().uppercase(),
                command.occurredAt.toString(),
                payload,
            )
            val verification = Mediator.capabilities.call(
                VerifyRefundResult.Request(
                    channelId = command.channelId,
                    notificationId = command.notificationId,
                    payload = payload,
                    refundId = command.refundId.toString(),
                    refundAttemptId = command.refundAttemptId,
                    channelRefundId = command.channelRefundId,
                    amount = command.amount,
                    currency = command.currency,
                )
            )
            val refund = Mediator.repositories.findOne(
                SRefund.predicateById(command.refundId)
            ) ?: throw RefundNotFoundException(command.refundId)
            val acceptedOperation = operations.replayOrNull(
                merchantId = refund.merchantId,
                commandType = COMMAND_TYPE,
                idempotencyKey = operationIdentity,
                canonicalRequestHash = operationIdentity,
            )
            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(refund.paymentId)
            ) ?: throw PaymentNotFoundException(refund.paymentId)
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
            when {
                outcome.disposition == RefundResultDisposition.CONFLICT -> manualReviewSupport.openRefundResultReview(
                    refund, command.refundAttemptId, command.notificationId, "REFUND_RESULT_CONFLICT",
                    outcome.conflictSummary ?: "退款渠道结果与既有事实冲突",
                )
                outcome.disposition == RefundResultDisposition.ATTEMPT_NOT_FOUND -> manualReviewSupport.openRefundResultReview(
                    refund, command.refundAttemptId, command.notificationId, "REFUND_ATTEMPT_NOT_FOUND",
                    outcome.rejectionSummary ?: "退款渠道结果引用了不存在的退款尝试",
                )
                outcome.refundStatus == RefundStatus.RESULT_UNKNOWN -> manualReviewSupport.openRefundResultReview(
                    refund, command.refundAttemptId, command.notificationId, "REFUND_RESULT_UNKNOWN",
                    "退款渠道结果未知；退款预算继续占用，等待同一退款尝试收敛",
                )
            }
            if (outcome.reservationConvertedToSuccessNow || outcome.reservationReleasedNow) {
                notifications.createAndDeliver(
                    MerchantNotificationService.Intent(
                        merchantId = refund.merchantId,
                        sourceKind = "REFUND",
                        sourceFactIdentity = "refund:${refund.id}:final:${refund.status.name}",
                        paymentId = refund.paymentId,
                        content = mapOf(
                            "merchantId" to refund.merchantId,
                            "paymentId" to refund.paymentId.toString(),
                            "refundId" to refund.id.toString(),
                            "merchantRefundNo" to refund.merchantRefundNumber,
                            "channelRefundId" to requireNotNull(refund.channelRefundId),
                            "amount" to refund.amount.toPlainString(),
                            "currency" to refund.currency,
                            "status" to refund.status.name,
                        ),
                    ),
                )
            }
            val receipt = acceptedOperation?.let { operations.receipt(it, replay = true) }
                ?: operations.accept(
                    merchantId = refund.merchantId,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = operationIdentity,
                    canonicalRequestHash = operationIdentity,
                    resourceType = "Refund",
                    resourceId = refund.id.toString(),
                    resourceUrl = "/api/refunds/${refund.id}",
                )
            return Response(outcome, receipt)
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
        val refundId: RefundId,
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
        /** callback 的 canonical raw payload；验真结论不来自调用方。 */
        val rawPayload: String? = null,
    ) : Command<Response>

    data class Response(
        val outcome: RefundResultRecordingOutcome,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "ReceiveRefundChannelResult"
}
