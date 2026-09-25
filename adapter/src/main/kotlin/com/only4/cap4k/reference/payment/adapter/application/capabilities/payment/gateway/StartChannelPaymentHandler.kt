package com.only4.cap4k.reference.payment.adapter.application.capabilities.payment.gateway

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferencePaymentChannelScriptRegistry
import com.only4.cap4k.reference.payment.application.capabilities.payment.gateway.StartChannelPayment
import com.only4.cap4k.reference.payment.application.capabilities.payment.gateway.PaymentChannelSubmissionOutcome
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "StartChannelPayment",
    packageName = "payment.gateway",
    description = "Submit a payment attempt to the selected external channel",
    aggregates = ["Payment"],
    family = "capability-handler"
)
class StartChannelPaymentHandler(
    private val scripts: ReferencePaymentChannelScriptRegistry,
) : CapabilityHandler<StartChannelPayment.Request, StartChannelPayment.Response> {

    override fun call(request: StartChannelPayment.Request): StartChannelPayment.Response {
        val script = scripts.scriptFor(request.channelId)
        if (script == ReferencePaymentChannelScriptRegistry.Script.REJECT_ON_SUBMIT || request.channelId != "C-001") {
            return StartChannelPayment.Response(
                outcome = PaymentChannelSubmissionOutcome.REJECTED,
                channelReference = null,
                failureCode = "UNSUPPORTED_CHANNEL",
                diagnosticSummary = "支付渠道拒绝了提交请求",
            )
        }
        if (script == ReferencePaymentChannelScriptRegistry.Script.NO_RESULT) {
            return StartChannelPayment.Response(
                outcome = PaymentChannelSubmissionOutcome.RESULT_UNKNOWN,
                channelReference = "reference-${request.requestIdentity}",
                failureCode = "REFERENCE_CHANNEL_NO_RESULT",
                diagnosticSummary = "支付渠道提交结果未知，保留原提交身份等待收敛",
            )
        }
        return StartChannelPayment.Response(
            outcome = PaymentChannelSubmissionOutcome.ACCEPTED,
            channelReference = "reference-${request.requestIdentity}",
            failureCode = null,
            diagnosticSummary = "支付渠道已受理请求",
        )
    }
}
