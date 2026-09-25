package com.only4.cap4k.reference.payment.application.commands.payment.create

import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.factory.PaymentFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "CreatePayment",
    packageName = "payment.create",
    description = "Create an idempotent payment intent for a merchant order",
    aggregates = ["Payment", "MerchantChannelConfiguration"],
    family = "command"
)
object CreatePaymentCmd {

    @Service
    class Handler(
        private val clock: Clock,
        private val operationSupport: OperationSupport,
        private val referencePolicy: ReferencePolicyService,
    ) : CommandHandler<Request, Response> {

        /**
         * 先验证输入和渠道资格，再以 merchant+idempotencyKey 查找既有 Payment；相同意图复用，内容冲突拒绝。
         * merchantOrder 成功唯一性与创建幂等是两个独立边界，不能用新幂等键绕过已付款订单。
         */
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val merchantOrderNumber = command.merchantOrderNumber.trim()
            val idempotencyKey = command.idempotencyKey.trim()
            val paymentMethod = command.paymentMethod.trim().uppercase()
            require(merchantId.isNotBlank()) { "商户身份不能为空" }
            require(merchantOrderNumber.isNotBlank()) { "商户订单号不能为空" }
            require(idempotencyKey.isNotBlank()) { "幂等键不能为空" }
            require(paymentMethod.isNotBlank()) { "支付方式不能为空" }

            val effectivePolicy = referencePolicy.current()
            val money = referencePolicy.money(command.amount, command.currency, effectivePolicy)
            val now = Instant.now(clock)
            val expiresAt = now.plus(effectivePolicy.paymentExpiry)
            val requestHash = operationSupport.canonicalHash(
                merchantOrderNumber,
                money.amount.toPlainString(),
                money.currency,
                paymentMethod,
            )
            operationSupport.replayOrNull(
                merchantId = merchantId,
                commandType = COMMAND_TYPE,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = requestHash,
            )?.let { operation ->
                val payment = Mediator.repositories.findOne(SPayment.predicateById(PaymentId.parse(operation.resourceId)))
                    ?: throw IllegalStateException("operation ${operation.id} refers to missing payment ${operation.resourceId}")
                return response(payment, idempotentReplay = true, receipt = operationSupport.receipt(operation, replay = true))
            }

            Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.currency eq money.currency) and
                        (schema.paymentMethod eq paymentMethod) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE) and
                        (schema.minimumAmount le money.amount) and
                        (schema.maximumAmount ge money.amount)
                }
            ) ?: throw NoEligibleChannelException("merchant $merchantId and ${money.currency}/$paymentMethod")

            val existing = Mediator.repositories.findOne(
                SPayment.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.idempotencyKey eq idempotencyKey)
                }
            )
            if (existing != null) {
                val sameIntent = existing.merchantOrderNumber == merchantOrderNumber &&
                    existing.amount.compareTo(money.amount) == 0 &&
                    existing.currency == money.currency &&
                    existing.paymentMethod == paymentMethod
                if (!sameIntent) {
                    throw PaymentConflictException(
                        code = "IDEMPOTENCY_CONFLICT",
                        message = "幂等键已绑定到内容不同的支付意图",
                    )
                }
                return response(
                    existing,
                    idempotentReplay = true,
                    receipt = operationSupport.accept(
                        merchantId = merchantId,
                        commandType = COMMAND_TYPE,
                        idempotencyKey = idempotencyKey,
                        canonicalRequestHash = requestHash,
                        resourceType = "PaymentIntent",
                        resourceId = existing.id.toString(),
                        resourceUrl = "/api/payments/${existing.id}",
                    ),
                )
            }

            Mediator.repositories.find(
                SPayment.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.merchantOrderNumber eq merchantOrderNumber)
                }
            ).firstOrNull { it.status !in setOf(PaymentStatus.FAILED, PaymentStatus.CLOSED) }?.let { orderPayment ->
                if (orderPayment.status == PaymentStatus.SUCCEEDED) {
                    throw PaymentConflictException(
                        code = "ORDER_ALREADY_PAID",
                        message = "商户订单 $merchantOrderNumber 已经存在成功支付",
                    )
                }
                throw PaymentConflictException(
                    code = "ORDER_ACTIVE_PAYMENT_EXISTS",
                    message = "商户订单 $merchantOrderNumber 已经存在活动支付",
                )
            }

            val payment = Mediator.factories.create<PaymentFactory.Payload, com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment>(
                PaymentFactory.Payload(
                    merchantId = merchantId,
                    merchantOrderNumber = merchantOrderNumber,
                    idempotencyKey = idempotencyKey,
                    amount = money.amount,
                    currency = money.currency,
                    paymentMethod = paymentMethod,
                    status = PaymentStatus.PENDING,
                    expiresAt = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                    succeededAt = null,
                    channelTransactionId = null,
                    lastRejectionSummary = null,
                    lastConflictSummary = null,
                    reservedRefundAmount = BigDecimal.ZERO,
                    successfulRefundAmount = BigDecimal.ZERO,
                )
            )
            return response(
                payment,
                idempotentReplay = false,
                receipt = operationSupport.accept(
                    merchantId = merchantId,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = idempotencyKey,
                    canonicalRequestHash = requestHash,
                    resourceType = "PaymentIntent",
                    resourceId = payment.id.toString(),
                    resourceUrl = "/api/payments/${payment.id}",
                ),
            )
        }

        private fun response(
            payment: com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment,
            idempotentReplay: Boolean,
            receipt: OperationReceipt,
        ) = Response(
            paymentId = payment.id,
            status = payment.status.name,
            idempotentReplay = idempotentReplay,
            rejectionCode = null,
            rejectionSummary = null,
            receipt = receipt,
        )
    }

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 商户订单号
         */
        val merchantOrderNumber: String,
        /**
         * 幂等键
         */
        val idempotencyKey: String,
        /**
         * 金额
         */
        val amount: BigDecimal,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 支付方式
         */
        val paymentMethod: String,
    ) : Command<Response>

    data class Response(
        /**
         * 支付标识
         */
        val paymentId: PaymentId,
        /**
         * 状态
         */
        val status: String,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean,
        /**
         * 拒绝代码
         */
        val rejectionCode: String?,
        /**
         * 拒绝摘要
         */
        val rejectionSummary: String?,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "CreatePaymentIntent"
}
