package com.only4.cap4k.reference.payment.application.commands.refund.create

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.errors.RefundConflictException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.reserveRefund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.factory.RefundFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "RequestRefund",
    packageName = "refund.request",
    description = "Accept an independent refund request and atomically reserve the payment refund budget",
    aggregates = ["Payment", "Refund"],
    family = "command",
)
object RequestRefundCmd {

    @Service
    class Handler(
        private val clock: Clock,
        private val operationSupport: OperationSupport,
        private val referencePolicy: ReferencePolicyService,
    ) : CommandHandler<Request, Response> {
        /**
         * 退款申请是预算占用的唯一入口。渠道路由、attempt 创建与网关提交分别由后续命令负责，
         * 因而相同申请重放绝不形成第二次占用或渠道副作用。
         */
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val merchantRefundNumber = command.merchantRefundNumber.trim()
            val idempotencyKey = command.idempotencyKey.trim()
            val reason = command.reason.trim()
            require(merchantId.isNotBlank()) { "商户身份不能为空" }
            require(merchantRefundNumber.isNotBlank()) { "商户退款号不能为空" }
            require(idempotencyKey.isNotBlank()) { "退款申请幂等键不能为空" }
            require(reason.isNotBlank()) { "退款原因不能为空" }
            val effectivePolicy = referencePolicy.current()
            val money = referencePolicy.money(command.amount, command.currency, effectivePolicy)
            val requestedAt = LocalDateTime.now(clock)
            val requestHash = operationSupport.canonicalHash(
                command.paymentId.toString(),
                merchantRefundNumber,
                money.amount.toPlainString(),
                money.currency,
                reason,
            )
            operationSupport.replayOrNull(
                merchantId = merchantId,
                commandType = COMMAND_TYPE,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = requestHash,
            )?.let { operation ->
                val refund = Mediator.repositories.findOne(SRefund.predicateById(RefundId.parse(operation.resourceId)))
                    ?: throw IllegalStateException("operation ${operation.id} refers to missing refund ${operation.resourceId}")
                return response(refund, idempotentReplay = true, receipt = operationSupport.receipt(operation, replay = true))
            }

            Mediator.repositories.findOne(
                SRefund.predicate { schema ->
                    (schema.merchantId eq merchantId) and (schema.idempotencyKey eq idempotencyKey)
                },
            )?.let { existing ->
                if (!existing.sameRequest(command.paymentId, merchantRefundNumber, money.amount, money.currency, reason)) {
                    throw RefundConflictException(
                        code = "IDEMPOTENCY_CONFLICT",
                        message = "退款申请幂等键已绑定到内容不同的退款申请",
                    )
                }
                return response(
                    existing,
                    idempotentReplay = true,
                    receipt = accept(existing, merchantId, idempotencyKey, requestHash),
                )
            }

            Mediator.repositories.findOne(
                SRefund.predicate { schema ->
                    (schema.merchantId eq merchantId) and (schema.merchantRefundNumber eq merchantRefundNumber)
                },
            )?.let {
                throw RefundConflictException(
                    code = "MERCHANT_REFUND_NUMBER_CONFLICT",
                    message = "商户退款号已绑定到另一退款申请",
                )
            }

            val payment = Mediator.repositories.findOne(
                SPayment.predicateById(command.paymentId),
            ) ?: throw PaymentNotFoundException(command.paymentId)
            require(payment.merchantId == merchantId) { "支付单不属于商户 $merchantId" }
            require(payment.currency == money.currency) {
                "支付单 ${payment.id} 的币种 ${payment.currency} 与退款币种 ${money.currency} 不一致"
            }
            require(payment.status == PaymentStatus.SUCCEEDED) {
                "支付单 ${payment.id} 当前状态为 ${payment.status}，不能退款"
            }
            val succeededAt = requireNotNull(payment.succeededAt) { "支付单 ${payment.id} 缺少成功时间" }
            require(!requestedAt.isBefore(succeededAt)) { "退款申请时间不能早于支付成功时间" }
            val deadline = succeededAt.plus(effectivePolicy.refundWindow)
            require(!requestedAt.isAfter(deadline)) { "退款期限已于 $deadline 结束" }

            payment.reserveRefund(money.amount)
            val refund = Mediator.factories.create<RefundFactory.Payload, Refund>(
                RefundFactory.Payload(
                    paymentId = payment.id,
                    merchantId = merchantId,
                    merchantRefundNumber = merchantRefundNumber,
                    idempotencyKey = idempotencyKey,
                    amount = money.amount,
                    currency = money.currency,
                    reason = reason,
                    paymentMethod = payment.paymentMethod,
                    status = RefundStatus.REQUESTED,
                    requestedAt = requestedAt,
                    refundDeadlineAt = deadline,
                    channelAcceptedAt = null,
                    finalizedAt = null,
                    reviewRequiredAt = null,
                    channelId = null,
                    channelConfigurationId = null,
                    channelConfigurationSnapshot = null,
                    requestIdentity = null,
                    channelRefundId = null,
                    lastNotificationIdentity = null,
                    lastNotificationReceivedAt = null,
                    lastRejectionSummary = null,
                    lastConflictSummary = null,
                    attempts = emptyList(),
                ),
            )
            return response(
                refund,
                idempotentReplay = false,
                receipt = accept(refund, merchantId, idempotencyKey, requestHash),
            )
        }

        private fun accept(
            refund: Refund,
            merchantId: String,
            idempotencyKey: String,
            requestHash: String,
        ): OperationReceipt = operationSupport.accept(
            merchantId = merchantId,
            commandType = COMMAND_TYPE,
            idempotencyKey = idempotencyKey,
            canonicalRequestHash = requestHash,
            resourceType = "Refund",
            resourceId = refund.id.toString(),
            resourceUrl = "/api/refunds/${refund.id}",
        )

        private fun response(
            refund: Refund,
            idempotentReplay: Boolean,
            receipt: OperationReceipt,
        ) = Response(
            refundId = refund.id,
            status = refund.status.name,
            idempotentReplay = idempotentReplay,
            receipt = receipt,
        )
    }

    private fun Refund.sameRequest(
        paymentId: PaymentId,
        merchantRefundNumber: String,
        amount: BigDecimal,
        currency: String,
        reason: String,
    ): Boolean =
        this.paymentId == paymentId &&
            this.merchantRefundNumber == merchantRefundNumber &&
            this.amount.compareTo(amount) == 0 &&
            this.currency == currency &&
            this.reason == reason

    data class Request(
        val merchantId: String,
        val idempotencyKey: String,
        val merchantRefundNumber: String,
        val paymentId: PaymentId,
        val amount: BigDecimal,
        val currency: String,
        val reason: String,
    ) : Command<Response>

    data class Response(
        val refundId: RefundId,
        val status: String,
        val idempotentReplay: Boolean,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "RequestRefund"
}
