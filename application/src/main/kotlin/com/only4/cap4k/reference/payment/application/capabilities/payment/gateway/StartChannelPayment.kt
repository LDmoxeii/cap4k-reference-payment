package com.only4.cap4k.reference.payment.application.capabilities.payment.gateway

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import java.math.BigDecimal

@DesignBlockMetadata(
    tag = "capability",
    name = "StartChannelPayment",
    packageName = "payment.gateway",
    description = "Submit a payment attempt to the selected external channel",
    aggregates = ["Payment"],
    family = "capability"
)
object StartChannelPayment {

    data class Request(
        /**
         * 支付尝试标识
         */
        val paymentAttemptId: String,
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
        /** ACCEPTED, REJECTED, or RESULT_UNKNOWN. Submission acceptance is never payment success. */
        val outcome: PaymentChannelSubmissionOutcome,
        /**
         * 渠道引用
         */
        val channelReference: String?,
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

enum class PaymentChannelSubmissionOutcome {
    ACCEPTED,
    REJECTED,
    RESULT_UNKNOWN,
}
