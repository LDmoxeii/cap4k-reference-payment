package com.only4.cap4k.reference.payment.application.capabilities.refund.gateway

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "StartChannelRefund",
    packageName = "refund.gateway",
    description = "Submit a refund attempt to the selected external channel",
    aggregates = ["Refund"],
    family = "capability"
)
object StartChannelRefund {

    data class Request(
        /**
         * 退款尝试标识
         */
        val refundAttemptId: String,
        /**
         * 渠道标识
         */
        val channelId: String,
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
         * 渠道退款标识
         */
        val channelRefundId: String?,
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
