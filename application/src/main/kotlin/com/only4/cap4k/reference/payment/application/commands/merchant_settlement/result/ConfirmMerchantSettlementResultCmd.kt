package com.only4.cap4k.reference.payment.application.commands.merchant_settlement.result

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.result.VerifySettlementResult
import com.only4.cap4k.reference.payment.application.errors.MerchantSettlementNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_settlement.SMerchantSettlement
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.SettlementExecutionAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.recordSettlementResult
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementResultRecordingOutcome
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "ConfirmMerchantSettlementResult",
    packageName = "merchant_settlement.result",
    description = "Verify and adjudicate one external settlement result notification",
    aggregates = ["MerchantSettlement"],
    family = "command"
)
object ConfirmMerchantSettlementResultCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val verification = Mediator.capabilities.call(
                VerifySettlementResult.Request(
                    channelId = command.channelId,
                    notificationId = command.notificationId,
                    settlementId = command.settlementId,
                    executionAttemptId = command.executionAttemptId,
                    executionGroupIdentity = command.executionGroupIdentity,
                    requestIdentity = command.requestIdentity,
                    externalSettlementIdentity = command.externalSettlementIdentity,
                    amount = command.amount,
                    currency = command.currency,
                    result = command.result,
                    resultCode = command.resultCode,
                    occurredAt = command.occurredAt,
                    verificationMaterial = command.verificationMaterial,
                )
            )
            val settlement = Mediator.repositories.findOne(
                SMerchantSettlement.predicateById(MerchantSettlementId.parse(command.settlementId))
            ) ?: throw MerchantSettlementNotFoundException(command.settlementId)
            val payloadFingerprint = sha256(
                listOf(
                    command.channelId,
                    command.settlementId,
                    command.executionAttemptId,
                    command.executionGroupIdentity,
                    command.requestIdentity,
                    command.externalSettlementIdentity,
                    command.amount.toPlainString(),
                    command.currency.trim().uppercase(),
                    command.result.trim().uppercase(),
                    command.resultCode.orEmpty(),
                    command.occurredAt.toString(),
                ).joinToString("|")
            )
            val outcome = settlement.recordSettlementResult(
                attemptId = SettlementExecutionAttemptId.parse(command.executionAttemptId),
                notificationIdentity = command.notificationId,
                payloadFingerprint = payloadFingerprint,
                channelId = command.channelId,
                executionGroupIdentity = command.executionGroupIdentity,
                requestIdentity = command.requestIdentity,
                externalSettlementIdentity = command.externalSettlementIdentity,
                amount = command.amount,
                currency = command.currency,
                result = verification.normalizedResult,
                resultCode = command.resultCode,
                occurredAt = LocalDateTime.ofInstant(command.occurredAt, ZoneOffset.UTC),
                receivedAt = LocalDateTime.ofInstant(command.receivedAt, ZoneOffset.UTC),
                verified = verification.verified,
                verificationSummary = verification.verificationSummary,
            )
            return Response(outcome)
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    data class Request(
        val channelId: String,
        val notificationId: String,
        val settlementId: String,
        val executionAttemptId: String,
        val executionGroupIdentity: String,
        val requestIdentity: String,
        val externalSettlementIdentity: String,
        val amount: BigDecimal,
        val currency: String,
        val result: String,
        val resultCode: String?,
        val occurredAt: Instant,
        val receivedAt: Instant,
        val verificationMaterial: String
    ) : Command<Response>

    data class Response(val outcome: SettlementResultRecordingOutcome)
}
