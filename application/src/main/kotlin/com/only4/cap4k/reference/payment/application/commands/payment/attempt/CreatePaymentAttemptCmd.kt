package com.only4.cap4k.reference.payment.application.commands.payment.attempt

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.PaymentNotFoundException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.createAttempt
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "CreatePaymentAttempt",
    packageName = "payment.attempt",
    description = "Create one explicit payment attempt without submitting it to a channel",
    aggregates = ["Payment", "MerchantChannelConfiguration"],
    family = "command",
)
object CreatePaymentAttemptCmd {
    @Service
    class Handler(
        private val clock: Clock,
        private val operationSupport: OperationSupport,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val idempotencyKey = command.idempotencyKey.trim()
            require(idempotencyKey.isNotBlank()) { "支付尝试创建幂等键不能为空" }
            val payment = Mediator.repositories.findOne(SPayment.predicateById(command.paymentId))
                ?: throw PaymentNotFoundException(command.paymentId)
            val normalizedRiskReason = command.riskReason?.trim()?.takeIf { it.isNotEmpty() }
            val requestHash = operationSupport.canonicalHash(payment.id.toString(), normalizedRiskReason)
            operationSupport.replayOrNull(payment.merchantId, COMMAND_TYPE, idempotencyKey, requestHash)?.let { operation ->
                val attempt = payment.attempts.firstOrNull { it.id.toString() == operation.resourceId }
                    ?: throw IllegalStateException("operation ${operation.id} refers to a payment attempt outside payment ${payment.id}")
                return response(payment, attempt, operationSupport.receipt(operation, replay = true))
            }

            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq payment.merchantId) and
                        (schema.currency eq payment.currency) and
                        (schema.paymentMethod eq payment.paymentMethod) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE) and
                        (schema.minimumAmount le payment.amount) and
                        (schema.maximumAmount ge payment.amount)
                },
            ) ?: throw NoEligibleChannelException("payment ${payment.id}")
            val now = LocalDateTime.now(clock)
            val requestIdentity = "payment-request:${payment.id}:${payment.attemptCount + 1}"
            val configurationSnapshot = listOf(
                "channelId=${configuration.channelId}",
                "currency=${configuration.currency}",
                "paymentMethod=${configuration.paymentMethod}",
                "minimumAmount=${configuration.minimumAmount}",
                "maximumAmount=${configuration.maximumAmount}",
                "routingPriority=${configuration.routingPriority}",
            ).joinToString(";")
            val attempt = payment.createAttempt(
                channelId = configuration.channelId,
                channelConfigurationId = configuration.id.toString(),
                channelConfigurationSnapshot = configurationSnapshot,
                requestIdentity = requestIdentity,
                initiatedAt = now,
                interactionInformation = "reference-channel=${configuration.channelId};requestIdentity=$requestIdentity",
                riskReason = normalizedRiskReason,
            )
            return response(
                payment,
                attempt,
                operationSupport.accept(
                    merchantId = payment.merchantId,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = idempotencyKey,
                    canonicalRequestHash = requestHash,
                    resourceType = "PaymentAttempt",
                    resourceId = attempt.id.toString(),
                    resourceUrl = "/api/payments/${payment.id}",
                ),
            )
        }

        private fun response(
            payment: com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment,
            attempt: com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttempt,
            receipt: OperationReceipt,
        ) = Response(
            paymentId = payment.id.toString(),
            paymentAttemptId = attempt.id.toString(),
            paymentStatus = payment.status.name,
            attemptStatus = attempt.status.name,
            channelId = attempt.channelId,
            requestIdentity = attempt.requestIdentity,
            interactionInformation = attempt.interactionInformation.orEmpty(),
            riskReason = attempt.riskReason,
            receipt = receipt,
        )
    }

    data class Request(val paymentId: PaymentId, val idempotencyKey: String, val riskReason: String? = null) : Command<Response>

    data class Response(
        val paymentId: String,
        val paymentAttemptId: String,
        val paymentStatus: String,
        val attemptStatus: String,
        val channelId: String,
        val requestIdentity: String,
        val interactionInformation: String,
        val riskReason: String?,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "CreatePaymentAttempt"
}
