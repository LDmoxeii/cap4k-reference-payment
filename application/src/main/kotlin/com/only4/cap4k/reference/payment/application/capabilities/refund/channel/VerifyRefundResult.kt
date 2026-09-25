package com.only4.cap4k.reference.payment.application.capabilities.refund.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "VerifyRefundResult",
    packageName = "refund.channel",
    description = "Verify refund-channel authenticity without placing credentials in the aggregate",
    aggregates = ["Refund"],
    family = "capability"
)
object VerifyRefundResult {

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
         * 载荷
         */
        val payload: String,
        /**
         * 退款标识
         */
        val refundId: String,
        /**
         * 退款尝试标识
         */
        val refundAttemptId: String,
        /**
         * 渠道退款标识
         */
        val channelRefundId: String,
        /**
         * 金额
         */
        val amount: BigDecimal,
        /**
         * 币种
         */
        val currency: String,
    ) : CapabilityCall<Response>

    data class Response(
        /**
         * 是否核验通过
         */
        val verified: Boolean,
        /**
         * 核验摘要
         */
        val verificationSummary: String?
    )

}
