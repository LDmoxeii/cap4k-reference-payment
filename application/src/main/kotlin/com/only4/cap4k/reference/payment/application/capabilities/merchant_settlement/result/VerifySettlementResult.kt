package com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.result

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal
import java.time.Instant

@DesignBlockMetadata(
    tag = "capability",
    name = "VerifySettlementResult",
    packageName = "merchant_settlement.result",
    description = "Verify and normalize one reference settlement result notification",
    aggregates = ["MerchantSettlement"],
    family = "capability"
)
object VerifySettlementResult {

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
         * 由 adapter 规范化后的原始 callback payload；不包含验真结论或 proof
         */
        val payload: String,
    ) : CapabilityCall<Response>

    data class Response(
        /**
         * 是否核验通过
         */
        val verified: Boolean,
        /**
         * 规范化结果
         */
        val normalizedResult: String,
        /**
         * 核验摘要
         */
        val verificationSummary: String?
    )

}
