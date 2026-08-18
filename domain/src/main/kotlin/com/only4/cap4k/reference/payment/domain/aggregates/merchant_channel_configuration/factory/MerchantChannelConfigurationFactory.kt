package com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.MerchantChannelConfiguration
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "MerchantChannelConfiguration",
    name = "MerchantChannelConfigurationFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.factory",
    description = "",
    type = "factory",
    root = false
)
class MerchantChannelConfigurationFactory : AggregateFactory<MerchantChannelConfigurationFactory.Payload, MerchantChannelConfiguration> {

    override fun create(entityPayload: Payload): MerchantChannelConfiguration =
        MerchantChannelConfiguration(
            merchantId = entityPayload.merchantId,
            channelId = entityPayload.channelId,
            currency = entityPayload.currency,
            paymentMethod = entityPayload.paymentMethod,
            minimumAmount = entityPayload.minimumAmount,
            maximumAmount = entityPayload.maximumAmount,
            status = entityPayload.status,
            routingPriority = entityPayload.routingPriority,
            channelRuleSummary = entityPayload.channelRuleSummary,
            activatedAt = entityPayload.activatedAt,
            retiredAt = entityPayload.retiredAt
        )

    data class Payload(
        val merchantId: String,
        val channelId: String,
        val currency: String,
        val paymentMethod: String,
        val minimumAmount: BigDecimal,
        val maximumAmount: BigDecimal,
        val status: MerchantChannelConfigurationStatus,
        val routingPriority: Int = 100,
        val channelRuleSummary: String,
        val activatedAt: LocalDateTime,
        val retiredAt: LocalDateTime?
    ) : AggregatePayload<MerchantChannelConfiguration>
}
