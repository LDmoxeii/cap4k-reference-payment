package com.only4.cap4k.reference.payment.application.commands.payment.create

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.factory.PaymentFactory
import com.only4.cap4k.reference.payment.domain.values.Money
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
    ) : CommandHandler<Request, Response> {

        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val merchantOrderNumber = command.merchantOrderNumber.trim()
            val idempotencyKey = command.idempotencyKey.trim()
            val paymentMethod = command.paymentMethod.trim().uppercase()
            require(merchantId.isNotBlank()) { "merchantId must not be blank" }
            require(merchantOrderNumber.isNotBlank()) { "merchantOrderNumber must not be blank" }
            require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
            require(paymentMethod.isNotBlank()) { "paymentMethod must not be blank" }

            val money = Money.of(command.amount, command.currency)
            val now = Instant.now(clock)
            require(command.expiresAt.isAfter(now)) { "payment expiresAt must be in the future" }

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
                    existing.paymentMethod == paymentMethod &&
                    existing.expiresAt == LocalDateTime.ofInstant(command.expiresAt, ZoneOffset.UTC)
                if (!sameIntent) {
                    throw PaymentConflictException(
                        code = "IDEMPOTENCY_CONFLICT",
                        message = "the idempotency key is already bound to a different payment intent",
                    )
                }
                return Response(
                    paymentId = existing.id.toString(),
                    status = existing.status.name,
                    idempotentReplay = true,
                    rejectionCode = null,
                    rejectionSummary = null,
                )
            }

            Mediator.repositories.findOne(
                SPayment.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.merchantOrderNumber eq merchantOrderNumber) and
                        (schema.status eq PaymentStatus.SUCCEEDED)
                }
            )?.let {
                throw PaymentConflictException(
                    code = "ORDER_ALREADY_PAID",
                    message = "merchant order $merchantOrderNumber already has a successful payment",
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
                    expiresAt = LocalDateTime.ofInstant(command.expiresAt, ZoneOffset.UTC),
                    succeededAt = null,
                    channelTransactionId = null,
                    lastRejectionSummary = null,
                    lastConflictSummary = null,
                    reservedRefundAmount = BigDecimal.ZERO,
                    successfulRefundAmount = BigDecimal.ZERO,
                )
            )
            return Response(
                paymentId = payment.id.toString(),
                status = payment.status.name,
                idempotentReplay = false,
                rejectionCode = null,
                rejectionSummary = null,
            )
        }
    }

    data class Request(
        val merchantId: String,
        val merchantOrderNumber: String,
        val idempotencyKey: String,
        val amount: BigDecimal,
        val currency: String,
        val paymentMethod: String,
        val expiresAt: Instant
    ) : Command<Response>

    data class Response(
        val paymentId: String,
        val status: String,
        val idempotentReplay: Boolean,
        val rejectionCode: String?,
        val rejectionSummary: String?
    )
}
