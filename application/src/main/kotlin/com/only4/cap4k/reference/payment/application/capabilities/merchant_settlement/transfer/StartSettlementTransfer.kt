package com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.transfer

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "StartSettlementTransfer",
    packageName = "merchant_settlement.transfer",
    description = "Submit one idempotent settlement transfer request to the reference provider",
    aggregates = ["MerchantSettlement"],
    family = "capability"
)
object StartSettlementTransfer {

    data class Request(
        /**
         * 结算标识
         */
        val merchantSettlementId: MerchantSettlementId,
        /**
         * 执行尝试标识
         */
        val executionAttemptId: String,
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 执行组身份
         */
        val executionGroupIdentity: String,
        /**
         * 请求身份
         */
        val requestIdentity: String,
        /**
         * 金额
         */
        val amount: BigDecimal,
        /**
         * 币种
         */
        val currency: String
    ) : CapabilityCall<Response>

    data class Response(
        /**
         * 是否接受
         */
        val accepted: Boolean,
        /**
         * 外部结算身份
         */
        val externalSettlementIdentity: String?,
        /**
         * 失败代码
         */
        val failureCode: String?,
        /**
         * 诊断摘要
         */
        val diagnosticSummary: String?
    )

}
