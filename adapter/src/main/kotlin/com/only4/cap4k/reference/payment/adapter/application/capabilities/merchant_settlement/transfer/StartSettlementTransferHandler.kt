package com.only4.cap4k.reference.payment.adapter.application.capabilities.merchant_settlement.transfer

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.merchant_settlement.transfer.StartSettlementTransfer
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "capability",
    name = "StartSettlementTransfer",
    packageName = "merchant_settlement.transfer",
    description = "Submit one idempotent settlement transfer request to the reference provider",
    aggregates = ["MerchantSettlement"],
    family = "capability-handler"
)
class StartSettlementTransferHandler : CapabilityHandler<StartSettlementTransfer.Request, StartSettlementTransfer.Response> {
    override fun call(request: StartSettlementTransfer.Request): StartSettlementTransfer.Response {
        if (request.channelId == "C-THROW") error("reference settlement provider outage")
        if (request.channelId != "C-001") {
            return StartSettlementTransfer.Response(
                accepted = false,
                externalSettlementIdentity = null,
                failureCode = "UNSUPPORTED_SETTLEMENT_CHANNEL",
                diagnosticSummary = "reference provider accepts only C-001",
            )
        }
        require(request.amount.signum() > 0) { "settlement transfer amount must be positive" }
        return StartSettlementTransfer.Response(
            accepted = true,
            externalSettlementIdentity = "STL-${request.requestIdentity}",
            failureCode = null,
            diagnosticSummary = "reference settlement transfer accepted",
        )
    }
}
