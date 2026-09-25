package com.only4.cap4k.reference.payment.application.commands.payment.attempt

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "StartPaymentAttempt",
    packageName = "payment.attempt",
    description = "Select an eligible merchant channel and start a durable payment attempt",
    aggregates = ["Payment", "MerchantChannelConfiguration"],
    family = "command"
)
object StartPaymentAttemptCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {

        override fun handle(command: Request): Response {

            return Response(
                paymentAttemptId = TODO("set paymentAttemptId"),
                channelId = TODO("set channelId"),
                requestIdentity = TODO("set requestIdentity"),
                paymentStatus = TODO("set paymentStatus"),
                attemptStatus = TODO("set attemptStatus"),
                diagnosticSummary = TODO("set diagnosticSummary")
            )
        }
    }

    data class Request(
        /**
         * 支付标识
         */
                val paymentId: PaymentId
    ) : Command<Response>

    data class Response(
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
         * 支付状态
         */
                val paymentStatus: String,
        /**
         * 尝试状态
         */
                val attemptStatus: String,
        /**
         * 诊断摘要
         */
                val diagnosticSummary: String?
    )

}
