package com.only4.cap4k.reference.payment.adapter.application.capabilities.refund.gateway
import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.refund.gateway.StartChannelRefund
import org.springframework.stereotype.Service
@Service
@DesignBlockMetadata(tag="capability",name="StartChannelRefund",packageName="refund.gateway",description="Submit a refund attempt to the selected external channel",aggregates=["Refund"],family="capability-handler")
class StartChannelRefundHandler : CapabilityHandler<StartChannelRefund.Request, StartChannelRefund.Response> {
 override fun call(request: StartChannelRefund.Request): StartChannelRefund.Response {
  if(request.channelId=="C-THROW") error("simulated deterministic channel outage")
  if(request.channelId!="C-001") return StartChannelRefund.Response(false,null,"UNSUPPORTED_CHANNEL","the deterministic fake refund gateway only accepts C-001")
  return StartChannelRefund.Response(true,"fake-refund-${request.requestIdentity}",null,"accepted by the deterministic fake refund gateway")
 }
}
