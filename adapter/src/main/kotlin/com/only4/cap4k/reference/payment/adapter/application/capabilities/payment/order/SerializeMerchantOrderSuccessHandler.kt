package com.only4.cap4k.reference.payment.adapter.application.capabilities.payment.order

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.payment.order.SerializeMerchantOrderSuccess
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.MerchantChannelConfiguration
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "SerializeMerchantOrderSuccess",
    packageName = "payment.order",
    description = "Serialize accepted-success decisions for one merchant before evaluating the merchant-order claim",
    aggregates = ["Payment", "MerchantChannelConfiguration"],
    family = "capability-handler",
)
class SerializeMerchantOrderSuccessHandler(
    private val entityManager: EntityManager,
) : CapabilityHandler<SerializeMerchantOrderSuccess.Request, SerializeMerchantOrderSuccess.Response> {
    override fun call(request: SerializeMerchantOrderSuccess.Request): SerializeMerchantOrderSuccess.Response {
        val configurations = entityManager.createQuery(
            """
            select configuration
            from MerchantChannelConfiguration configuration
            where configuration.merchantId = :merchantId
            order by configuration.id
            """.trimIndent(),
            MerchantChannelConfiguration::class.java,
        )
            .setParameter("merchantId", request.merchantId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
        check(configurations.isNotEmpty()) {
            "merchant ${request.merchantId} has no configuration row available for success serialization"
        }
        return SerializeMerchantOrderSuccess.Response(configurations.size)
    }
}
