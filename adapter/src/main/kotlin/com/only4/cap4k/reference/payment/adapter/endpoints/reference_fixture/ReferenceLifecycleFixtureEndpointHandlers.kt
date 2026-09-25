package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceLogicalClock
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.review.ReviewUnknownMerchantSettlementsCmd
import com.only4.cap4k.reference.payment.application.commands.payment.expiry.ExpirePaymentsCmd
import com.only4.cap4k.reference.payment.application.commands.reconciliation.run.RunDailyReconciliationCmd
import com.only4.cap4k.reference.payment.application.commands.reference_fixture.channel.ConfigureReferenceMerchantChannelCmd
import com.only4.cap4k.reference.payment.application.commands.refund.review.ReviewPendingRefundsCmd
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.ConfigureReferenceMerchantChannelEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.RunReferenceMaintenanceEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ConfigureReferenceMerchantChannelEndpointHandler(
    private val clock: ReferenceLogicalClock,
) : EndpointHandler<ConfigureReferenceMerchantChannelEndpoint.Request, ConfigureReferenceMerchantChannelEndpoint.Response> {
    override fun handle(request: ConfigureReferenceMerchantChannelEndpoint.Request): ConfigureReferenceMerchantChannelEndpoint.Response {
        val status = runCatching { MerchantChannelConfigurationStatus.valueOf(request.status.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("不支持的 reference merchant channel status：${request.status}") }
        val result = Mediator.commands.send(
            ConfigureReferenceMerchantChannelCmd.Request(
                merchantId = request.merchantId,
                channelId = request.channelId,
                currency = request.currency,
                paymentMethod = request.paymentMethod,
                status = status,
                minimumAmount = request.minimumAmount,
                maximumAmount = request.maximumAmount,
                routingPriority = request.routingPriority,
                refundWindowDays = request.refundWindowDays,
                refundResultReviewAfterMinutes = request.refundResultReviewAfterMinutes,
                settlementFeeBasisPoints = request.settlementFeeBasisPoints,
                settlementFixedFeeAmount = request.settlementFixedFeeAmount,
                settlementFeeRoundingMode = request.settlementFeeRoundingMode,
                settlementResultReviewAfterMinutes = request.settlementResultReviewAfterMinutes,
                changedAt = clock.instant(),
            ),
        )
        return ConfigureReferenceMerchantChannelEndpoint.Response(
            configurationId = result.configurationId,
            merchantId = request.merchantId.trim(),
            channelId = request.channelId.trim(),
            status = result.status,
            updated = result.updated,
        )
    }
}

@Component
class RunReferenceMaintenanceEndpointHandler(
    private val clock: ReferenceLogicalClock,
    @Value("\${payment.reconciliation.channel-id:C-001}") private val reconciliationChannelId: String,
    @Value("\${payment.reconciliation.currency:CNY}") private val reconciliationCurrency: String,
) : EndpointHandler<RunReferenceMaintenanceEndpoint.Request, RunReferenceMaintenanceEndpoint.Response> {
    override fun handle(request: RunReferenceMaintenanceEndpoint.Request): RunReferenceMaintenanceEndpoint.Response {
        val action = request.action.trim().uppercase()
        return when (action) {
            "PAYMENT_EXPIRY" -> Mediator.commands.send(ExpirePaymentsCmd.Request(clock.instant())).let {
                RunReferenceMaintenanceEndpoint.Response(action, it.inspectedCount, it.closedCount, it.reviewOpenedCount)
            }
            "REFUND_UNKNOWN_REVIEW" -> Mediator.commands.send(ReviewPendingRefundsCmd.Request(clock.instant())).let {
                RunReferenceMaintenanceEndpoint.Response(action, it.reviewedCount, it.reviewedCount, it.reviewedCount)
            }
            "SETTLEMENT_UNKNOWN_REVIEW" -> Mediator.commands.send(ReviewUnknownMerchantSettlementsCmd.Request(clock.instant())).let {
                RunReferenceMaintenanceEndpoint.Response(action, it.reviewedCount, it.reviewedCount, it.reviewedCount)
            }
            "RECONCILIATION_DAILY" -> Mediator.commands.send(
                RunDailyReconciliationCmd.Request(
                    channelId = reconciliationChannelId,
                    currency = reconciliationCurrency,
                    triggeredAt = clock.instant(),
                )
            ).let {
                RunReferenceMaintenanceEndpoint.Response(
                    action = action,
                    inspectedCount = 1,
                    changedCount = if (it.runId != null && !it.idempotentReplay) 1 else 0,
                    reviewOpenedCount = it.unresolvedDifferenceCount,
                )
            }
            else -> throw IllegalArgumentException("不支持的 reference maintenance action：$action")
        }
    }
}
