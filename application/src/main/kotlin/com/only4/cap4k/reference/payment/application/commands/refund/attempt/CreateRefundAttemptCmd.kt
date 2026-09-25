package com.only4.cap4k.reference.payment.application.commands.refund.attempt

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.errors.NoEligibleChannelException
import com.only4.cap4k.reference.payment.application.errors.RefundNotFoundException
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import com.only4.cap4k.reference.payment.domain.aggregates.refund.createAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.refund.enums.RefundAttemptStatus
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "CreateRefundAttempt",
    packageName = "refund.attempt",
    description = "Select a currently eligible channel and create a durable refund attempt without submitting it",
    aggregates = ["Refund", "MerchantChannelConfiguration"],
    family = "command",
)
object CreateRefundAttemptCmd {

    @Service
    class Handler(
        private val clock: Clock,
        private val operationSupport: OperationSupport,
        private val referencePolicy: ReferencePolicyService,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val idempotencyKey = command.idempotencyKey.trim()
            require(idempotencyKey.isNotBlank()) { "退款尝试创建幂等键不能为空" }
            val refund = Mediator.repositories.findOne(
                SRefund.predicateById(command.refundId),
            ) ?: throw RefundNotFoundException(command.refundId)
            val requestHash = operationSupport.canonicalHash(refund.id.toString())
            operationSupport.replayOrNull(
                merchantId = refund.merchantId,
                commandType = COMMAND_TYPE,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = requestHash,
            )?.let { operation ->
                val attempt = refund.attempts.firstOrNull { it.id.toString() == operation.resourceId }
                    ?: throw IllegalStateException("operation ${operation.id} refers to a refund attempt outside refund ${refund.id}")
                return response(
                    refund,
                    attempt,
                    reusedExistingAttempt = true,
                    receipt = operationSupport.receipt(operation, replay = true),
                )
            }

            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq refund.merchantId) and
                        (schema.currency eq refund.currency) and
                        (schema.paymentMethod eq refund.paymentMethod) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE) and
                        (schema.minimumAmount le refund.amount) and
                        (schema.maximumAmount ge refund.amount)
                },
            ) ?: throw NoEligibleChannelException("refund ${refund.id}")

            val existingAttemptIds = refund.attempts.map { it.id }.toSet()
            val effectivePolicy = referencePolicy.current()
            val reusableAttempt = refund.attempts.any {
                it.status in setOf(
                    RefundAttemptStatus.CREATED,
                    RefundAttemptStatus.SUBMITTED,
                    RefundAttemptStatus.ACCEPTED,
                )
            }
            require(reusableAttempt || refund.attempts.size < effectivePolicy.maxRefundAttempts) {
                "退款单 ${refund.id} 已达到 ReferencePolicy.maxRefundAttempts=${effectivePolicy.maxRefundAttempts}"
            }
            val requestIdentity = "${refund.id}:${refund.attempts.size + 1}"
            val snapshot = listOf(
                "channelId=${configuration.channelId}",
                "currency=${configuration.currency}",
                "paymentMethod=${configuration.paymentMethod}",
                "minimumAmount=${configuration.minimumAmount}",
                "maximumAmount=${configuration.maximumAmount}",
                "unknownResultReviewAfter=${effectivePolicy.unknownResultReviewAfter}",
            ).joinToString(";")
            val now = LocalDateTime.now(clock)
            val attempt = refund.createAttempt(
                now = now,
                channelId = configuration.channelId,
                configurationId = configuration.id.toString(),
                snapshot = snapshot,
                requestIdentity = requestIdentity,
                reviewAfterAt = now.plus(effectivePolicy.unknownResultReviewAfter),
            )
            return response(
                refund,
                attempt,
                reusedExistingAttempt = attempt.id in existingAttemptIds,
                receipt = operationSupport.accept(
                    merchantId = refund.merchantId,
                    commandType = COMMAND_TYPE,
                    idempotencyKey = idempotencyKey,
                    canonicalRequestHash = requestHash,
                    resourceType = "RefundAttempt",
                    resourceId = attempt.id.toString(),
                    resourceUrl = "/api/refunds/${refund.id}",
                ),
            )
        }

        private fun response(
            refund: com.only4.cap4k.reference.payment.domain.aggregates.refund.Refund,
            attempt: com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundAttempt,
            reusedExistingAttempt: Boolean,
            receipt: OperationReceipt,
        ) = Response(
            refundId = refund.id.toString(),
            refundAttemptId = attempt.id.toString(),
            refundStatus = refund.status.name,
            attemptStatus = attempt.status.name,
            channelId = attempt.channelId,
            requestIdentity = attempt.requestIdentity,
            reusedExistingAttempt = reusedExistingAttempt,
            receipt = receipt,
        )
    }

    data class Request(
        val refundId: RefundId,
        val idempotencyKey: String,
    ) : Command<Response>

    data class Response(
        val refundId: String,
        val refundAttemptId: String,
        val refundStatus: String,
        val attemptStatus: String,
        val channelId: String,
        val requestIdentity: String,
        val reusedExistingAttempt: Boolean,
        val receipt: OperationReceipt,
    )

    private const val COMMAND_TYPE = "CreateRefundAttempt"
}
