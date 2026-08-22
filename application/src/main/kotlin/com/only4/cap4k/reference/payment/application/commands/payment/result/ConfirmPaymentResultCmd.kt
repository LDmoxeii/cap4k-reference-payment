package com.only4.cap4k.reference.payment.application.commands.payment.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.payment.channel.VerifyPaymentResult
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.MerchantChannelConfigurationId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.SettlementFeeRule
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.values.ChannelResultRecordingOutcome
import com.only4.cap4k.reference.payment.domain.values.Money
import java.math.BigDecimal
import java.math.RoundingMode
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
            val paymentAttemptId = PaymentAttemptId.parse(command.paymentAttemptId)
            val attempt = payment.attempts.firstOrNull { it.id == paymentAttemptId }
            val settlementFeeRule = if (
                command.result.trim().equals("SUCCESS", ignoreCase = true) &&
                attempt != null &&
                attempt.channelId == command.channelId &&
                payment.amount.compareTo(command.amount) == 0 &&
                payment.currency == command.currency.trim().uppercase() &&
                verification.verified
            ) {
                val configuration = Mediator.repositories.findOne(
                    SMerchantChannelConfiguration.predicateById(
                        MerchantChannelConfigurationId.parse(attempt.channelConfigurationId)
                    )
                ) ?: throw IllegalArgumentException(
                    "Payment attempt ${attempt.id} references a missing merchant channel configuration"
                )
                require(configuration.merchantId == payment.merchantId) {
                    "Payment attempt ${attempt.id} references a configuration owned by another merchant"
                }
                require(configuration.channelId == command.channelId && configuration.channelId == attempt.channelId) {
                    "Payment attempt ${attempt.id} references a configuration for another channel"
                }
                require(configuration.currency == payment.currency && configuration.currency == command.currency.uppercase()) {
                    "Payment attempt ${attempt.id} references a configuration for another currency"
                }
                require(configuration.paymentMethod == payment.paymentMethod) {
                    "Payment attempt ${attempt.id} references a configuration for another payment method"
                }
                SettlementFeeRule(
                    configurationId = configuration.id.toString(),
                    basisPoints = configuration.settlementFeeBasisPoints,
                    fixedFeeAmount = configuration.settlementFixedFeeAmount,
                    roundingMode = try {
                        RoundingMode.valueOf(configuration.settlementFeeRoundingMode.trim().uppercase())
                    } catch (_: IllegalArgumentException) {
                        throw IllegalArgumentException(
                            "Unsupported settlement fee rounding mode: ${configuration.settlementFeeRoundingMode}"
                        )
                    },
                    currencyPrecision = Money.fractionDigits(payment.currency),
                )
            } else {
                null
            }
            val decision = payment.recordChannelResult(
                paymentAttemptId = paymentAttemptId,
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
                settlementFeeRule = settlementFeeRule,
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
