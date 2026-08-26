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
        /** 先核验 callback，再把 notification/payload/attempt 交给聚合统一裁决；成功事件只能由聚合首次 settled fact 触发。 */
        override fun handle(command: Request): Response {
            val verification = Mediator.capabilities.call(
                VerifySettlementResult.Request(
                    channelId = command.channelId,
                    notificationId = command.notificationId,
                    merchantSettlementId = command.merchantSettlementId,
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
                SMerchantSettlement.predicateById(command.merchantSettlementId)
            ) ?: throw MerchantSettlementNotFoundException(command.merchantSettlementId)
            val payloadFingerprint = sha256(
                listOf(
                    command.channelId,
                    command.merchantSettlementId,
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
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 通知标识
         */
        val notificationId: String,
        /**
         * 结算标识
         */
        val merchantSettlementId: MerchantSettlementId,
        /**
         * 执行尝试标识
         */
        val executionAttemptId: String,
        /**
         * 执行组身份
         */
        val executionGroupIdentity: String,
        /**
         * 请求身份
         */
        val requestIdentity: String,
        /**
         * 外部结算身份
         */
        val externalSettlementIdentity: String,
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
         * 结果代码
         */
        val resultCode: String?,
        /**
         * 发生时间
         */
        val occurredAt: Instant,
        /**
         * 接收时间
         */
        val receivedAt: Instant,
        /**
         * 核验材料
         */
        val verificationMaterial: String
    ) : Command<Response>

    data class Response(val outcome: SettlementResultRecordingOutcome)
}
