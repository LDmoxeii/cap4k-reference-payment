package com.only4.cap4k.reference.payment.adapter.application.capabilities.refund.channel
import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.reference.payment.application.capabilities.refund.channel.VerifyRefundResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
@Service
@DesignBlockMetadata(tag="capability",name="VerifyRefundResult",packageName="refund.channel",description="Verify refund-channel authenticity without placing credentials in the aggregate",aggregates=["Refund"],family="capability-handler")
class VerifyRefundResultHandler(@param:Value("\${payment.sandbox.channel-id}") private val trustedChannelId:String,@param:Value("\${payment.sandbox.verification-secret}") private val verificationSecret:String) : CapabilityHandler<VerifyRefundResult.Request, VerifyRefundResult.Response> {
 override fun call(request: VerifyRefundResult.Request)=VerifyRefundResult.Response(request.channelId==trustedChannelId && request.verificationMaterial==verificationSecret && request.notificationId.isNotBlank() && request.payload.isNotBlank(),"sandbox signature verification")
}
