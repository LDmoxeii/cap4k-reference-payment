package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.execution

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.transfer.StartSettlementTransfer
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementConflictException
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.markExecutionAccepted
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.rejectExecutionStart
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.startExecutionAttempt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "StartMerchantSettlementExecution",
    packageName = "merchant_settlement.execution",
    description = "Submit a confirmed positive settlement or retry after an explicit failed attempt",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object StartMerchantSettlementExecutionCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(MerchantSettlementId.parse(command.settlementId))
            ) ?: throw MerchantSettlementNotFoundException(command.settlementId)
            if (settlement.status in setOf(
                    MerchantSettlementStatus.RESULT_UNKNOWN,
                    MerchantSettlementStatus.CONFLICT_REVIEW_REQUIRED,
                )
            ) {
                throw MerchantSettlementConflictException(
                    code = "MERCHANT_SETTLEMENT_RESULT_UNRESOLVED",
                    message = "merchant settlement ${settlement.id} cannot create a new execution while its prior result is unresolved",
                )
            }
            val configuration = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq settlement.merchantId) and
                        (schema.channelId eq settlement.channelId) and
                        (schema.currency eq settlement.currency) and
                        (schema.status eq MerchantChannelConfigurationStatus.ACTIVE)
                }
            ) ?: error("no active channel configuration for settlement ${settlement.id}")
            val requestedAt = LocalDateTime.ofInstant(command.requestedAt, ZoneOffset.UTC)
            val groupIdentity = settlement.executionGroupIdentity ?: "SETTLEMENT:${settlement.id}"
            val requestIdentity = "$groupIdentity:${settlement.settlementExecutionAttempts.size + 1}"
            val attempt = settlement.startExecutionAttempt(
                operatorIdentity = command.operatorIdentity,
                operatorRole = command.operatorRole,
                requestedAt = requestedAt,
                reviewAfterMinutes = configuration.settlementResultReviewAfterMinutes,
                executionGroupIdentity = groupIdentity,
                requestIdentity = requestIdentity,
            )
            if (attempt.requestIdentity != requestIdentity) {
                return Response(
                    settlementId = settlement.id.toString(),
                    attemptId = attempt.id.toString(),
                    executionGroupIdentity = attempt.executionGroupIdentity,
                    requestIdentity = attempt.requestIdentity,
                    status = settlement.status.name,
                    providerAccepted = attempt.acceptedAt != null,
                    diagnosticSummary = "reused an existing processing attempt",
                )
            }
            val transfer = try {
                Mediator.capabilities.call(
                    StartSettlementTransfer.Request(
                        settlementId = settlement.id.toString(),
                        executionAttemptId = attempt.id.toString(),
                        merchantId = settlement.merchantId,
                        channelId = settlement.channelId,
                        executionGroupIdentity = groupIdentity,
                        requestIdentity = requestIdentity,
                        amount = settlement.netAmount,
                        currency = settlement.currency,
                    )
                )
            } catch (failure: RuntimeException) {
                val diagnostic = "settlement transfer gateway failed: ${failure.message ?: failure::class.simpleName}"
                settlement.rejectExecutionStart(attempt.id, "SETTLEMENT_GATEWAY_ERROR", diagnostic)
                return response(settlement, attempt.id.toString(), false, diagnostic)
            }
            if (!transfer.accepted || transfer.externalSettlementIdentity.isNullOrBlank()) {
                val diagnostic = listOfNotNull(
                    transfer.failureCode ?: "SETTLEMENT_GATEWAY_REJECTED",
                    transfer.diagnosticSummary,
                ).joinToString(": ")
                settlement.rejectExecutionStart(
                    attempt.id,
                    transfer.failureCode ?: "SETTLEMENT_GATEWAY_REJECTED",
                    transfer.diagnosticSummary,
                )
                return response(settlement, attempt.id.toString(), false, diagnostic)
            }
            settlement.markExecutionAccepted(attempt.id, transfer.externalSettlementIdentity, requestedAt)
            return response(settlement, attempt.id.toString(), true, transfer.diagnosticSummary)
        }

        private fun response(
            settlement: com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlement,
            attemptId: String,
            accepted: Boolean,
            diagnostic: String?,
        ): Response = Response(
            settlementId = settlement.id.toString(),
            attemptId = attemptId,
            executionGroupIdentity = settlement.executionGroupIdentity,
            requestIdentity = settlement.settlementExecutionAttempts.lastOrNull()?.requestIdentity,
            status = settlement.status.name,
            providerAccepted = accepted,
            diagnosticSummary = diagnostic,
        )
    }

    data class Request(
        val settlementId: String,
        val operatorIdentity: String,
        val operatorRole: String,
        val requestedAt: Instant
    ) : Command<Response>

    data class Response(
        val settlementId: String,
        val attemptId: String?,
        val executionGroupIdentity: String?,
        val requestIdentity: String?,
        val status: String,
        val providerAccepted: Boolean,
        val diagnosticSummary: String?
    )
}
