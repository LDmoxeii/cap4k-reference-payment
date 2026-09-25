package com.only4.cap4k.reference.payment.application.commands.reference_fixture.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.domain._share.meta.merchant_channel_configuration.SMerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.configureReferenceFixture
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.factory.MerchantChannelConfigurationFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/** Reference-only deterministic merchant/channel fixture, persisted through the normal aggregate UoW. */
@DesignBlockMetadata(
    tag = "command",
    name = "ConfigureReferenceMerchantChannel",
    packageName = "reference_fixture.channel",
    description = "Persist deterministic reference merchant routing input through the aggregate Unit of Work",
    aggregates = ["MerchantChannelConfiguration"],
    family = "command",
)
object ConfigureReferenceMerchantChannelCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim()
            val channelId = command.channelId.trim()
            val currency = command.currency.trim().uppercase()
            val paymentMethod = command.paymentMethod.trim().uppercase()
            require(merchantId.isNotBlank()) { "fixture merchantId 不能为空" }
            require(channelId.isNotBlank()) { "fixture channelId 不能为空" }
            require(currency.matches(Regex("[A-Z]{3}"))) { "fixture currency 必须是 ISO 4217 code" }
            require(paymentMethod.isNotBlank()) { "fixture paymentMethod 不能为空" }
            val changedAt = LocalDateTime.ofInstant(command.changedAt, ZoneOffset.UTC)
            val existing = Mediator.repositories.findOne(
                SMerchantChannelConfiguration.predicate { schema ->
                    (schema.merchantId eq merchantId) and
                        (schema.channelId eq channelId) and
                        (schema.currency eq currency) and
                        (schema.paymentMethod eq paymentMethod)
                },
            )
            val configuration = existing ?: Mediator.factories.create<
                MerchantChannelConfigurationFactory.Payload,
                com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.MerchantChannelConfiguration
            >(
                MerchantChannelConfigurationFactory.Payload(
                    merchantId = merchantId,
                    channelId = channelId,
                    currency = currency,
                    paymentMethod = paymentMethod,
                    minimumAmount = command.minimumAmount,
                    maximumAmount = command.maximumAmount,
                    status = command.status,
                    routingPriority = command.routingPriority,
                    channelRuleSummary = "reference HTTP acceptance fixture",
                    refundWindowDays = command.refundWindowDays,
                    refundResultReviewAfterMinutes = command.refundResultReviewAfterMinutes,
                    settlementFeeBasisPoints = command.settlementFeeBasisPoints,
                    settlementFixedFeeAmount = command.settlementFixedFeeAmount,
                    settlementFeeRoundingMode = command.settlementFeeRoundingMode,
                    settlementResultReviewAfterMinutes = command.settlementResultReviewAfterMinutes,
                    activatedAt = changedAt,
                    retiredAt = changedAt.takeIf { command.status == MerchantChannelConfigurationStatus.RETIRED },
                ),
            )
            if (existing != null) {
                configuration.configureReferenceFixture(
                    status = command.status,
                    minimumAmount = command.minimumAmount,
                    maximumAmount = command.maximumAmount,
                    refundWindowDays = command.refundWindowDays,
                    refundResultReviewAfterMinutes = command.refundResultReviewAfterMinutes,
                    settlementFeeBasisPoints = command.settlementFeeBasisPoints,
                    settlementFixedFeeAmount = command.settlementFixedFeeAmount,
                    settlementFeeRoundingMode = command.settlementFeeRoundingMode,
                    settlementResultReviewAfterMinutes = command.settlementResultReviewAfterMinutes,
                    changedAt = changedAt,
                )
            }
            return Response(configuration.id.toString(), configuration.status.name, existing != null)
        }
    }

    data class Request(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val paymentMethod: String,
        val status: MerchantChannelConfigurationStatus,
        val minimumAmount: BigDecimal,
        val maximumAmount: BigDecimal,
        val routingPriority: Int,
        val refundWindowDays: Int,
        val refundResultReviewAfterMinutes: Int,
        val settlementFeeBasisPoints: Int,
        val settlementFixedFeeAmount: BigDecimal,
        val settlementFeeRoundingMode: String,
        val settlementResultReviewAfterMinutes: Int,
        val changedAt: Instant,
    ) : Command<Response>

    data class Response(val configurationId: String, val status: String, val updated: Boolean)
}
