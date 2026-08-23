package com.only4.cap4k.reference.payment.adapter.application.capabilities.refund.gateway
import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.refund.gateway.StartChannelRefund
import org.springframework.stereotype.Service
@Service
@DesignBlockMetadata(tag="capability",name="StartChannelRefund",packageName="refund.gateway",description="Submit a refund attempt to the selected external channel",aggregates=["Refund"],family="capability-handler")
class StartChannelRefundHandler : CapabilityHandler<StartChannelRefund.Request, StartChannelRefund.Response> {
 override fun call(request: StartChannelRefund.Request): StartChannelRefund.Response {
  if(request.channelId=="C-THROW") error("模拟的确定性渠道故障")
  if(request.channelId!="C-001") return StartChannelRefund.Response(false,null,"UNSUPPORTED_CHANNEL","确定性 Fake 退款渠道仅接受 C-001")
  return StartChannelRefund.Response(true,"fake-refund-${request.requestIdentity}",null,"确定性 Fake 退款渠道已受理请求")
 }
}
