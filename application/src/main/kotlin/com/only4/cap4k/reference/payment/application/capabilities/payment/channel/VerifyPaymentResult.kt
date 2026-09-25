package com.only4.cap4k.reference.payment.application.capabilities.payment.channel

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "VerifyPaymentResult",
    packageName = "payment.channel",
    description = "Verify channel authenticity without placing credentials in the aggregate",
    aggregates = ["Payment"],
    family = "capability"
)
object VerifyPaymentResult {

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
         * 支付标识
         */
        val paymentId: String,
        /**
         * 支付尝试标识
         */
        val paymentAttemptId: String,
        /**
         * 渠道交易标识
         */
        val channelTransactionId: String,
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
