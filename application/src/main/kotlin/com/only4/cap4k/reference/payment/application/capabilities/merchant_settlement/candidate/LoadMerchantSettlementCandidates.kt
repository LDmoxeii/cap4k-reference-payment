package com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.candidate

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.values.SettlementCandidateFact
import java.time.Instant

@DesignBlockMetadata(
    tag = "capability",
    name = "LoadMerchantSettlementCandidates",
    packageName = "merchant_settlement.candidate",
    description = "Project eligible and excluded current-effective-run facts for a merchant settlement period",
    aggregates = ["MerchantSettlement"],
    family = "capability"
)
object LoadMerchantSettlementCandidates {

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 期间开始日期
         */
        val periodStart: Instant,
        /**
         * 期间结束日期
         */
        val periodEnd: Instant,
        /**
         * 业务时区
         */
        val businessTimezone: String
    ) : CapabilityCall<Response>

    data class Response(
        /**
         * 符合事实列表
         */
        val eligibleFacts: List<SettlementCandidateFact>,
        /**
         * 排除数量
         */
        val excludedCount: Int,
        /**
         * 阻塞摘要列表
         */
        val blockerSummaries: List<String>
    )

}
