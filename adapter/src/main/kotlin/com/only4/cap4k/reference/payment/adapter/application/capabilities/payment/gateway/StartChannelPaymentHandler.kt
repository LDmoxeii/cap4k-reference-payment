package com.only4.cap4k.reference.payment.adapter.application.capabilities.payment.gateway

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.payment.gateway.StartChannelPayment
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
class StartChannelPaymentHandler : CapabilityHandler<StartChannelPayment.Request, StartChannelPayment.Response> {

    override fun call(request: StartChannelPayment.Request): StartChannelPayment.Response {
        if (request.channelId == "C-THROW") {
            error("simulated deterministic channel outage")
        }
        if (request.channelId != "C-001") {
            return StartChannelPayment.Response(
                accepted = false,
                channelReference = null,
                failureCode = "UNSUPPORTED_CHANNEL",
                diagnosticSummary = "the deterministic fake gateway only accepts C-001",
            )
        }
        return StartChannelPayment.Response(
            accepted = true,
            channelReference = "fake-${request.requestIdentity}",
            failureCode = null,
            diagnosticSummary = "accepted by the deterministic fake channel gateway",
        )
    }
}
