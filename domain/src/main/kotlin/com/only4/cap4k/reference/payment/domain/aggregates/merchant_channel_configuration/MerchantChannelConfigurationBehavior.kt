package com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Place behavior for MerchantChannelConfiguration and its owned entities here.
 */
fun MerchantChannelConfiguration.onCreate() {
}

fun MerchantChannelConfiguration.onDeleted() {
}

/**
 * Reference-profile fixture behavior.  The HTTP fixture never writes the table directly: it loads
 * this aggregate through the repository and applies the same state change a configuration command
 * would apply. Historical payment attempts keep their own channel and fee snapshots.
 */
fun MerchantChannelConfiguration.configureReferenceFixture(
    status: MerchantChannelConfigurationStatus,
    minimumAmount: BigDecimal,
    maximumAmount: BigDecimal,
    refundWindowDays: Int,
    refundResultReviewAfterMinutes: Int,
    settlementFeeBasisPoints: Int,
    settlementFixedFeeAmount: BigDecimal,
    settlementFeeRoundingMode: String,
    settlementResultReviewAfterMinutes: Int,
    changedAt: LocalDateTime,
) {
    require(minimumAmount.signum() > 0) { "渠道最小金额必须为正数" }
    require(maximumAmount >= minimumAmount) { "渠道最大金额不能小于最小金额" }
    require(refundWindowDays > 0) { "退款窗口必须为正数" }
    require(refundResultReviewAfterMinutes > 0) { "退款复核等待时间必须为正数" }
    require(settlementResultReviewAfterMinutes > 0) { "结算复核等待时间必须为正数" }
    require(settlementFeeBasisPoints >= 0) { "手续费基点不能为负数" }
    require(settlementFixedFeeAmount.signum() >= 0) { "固定手续费不能为负数" }

    this.status = status
    this.minimumAmount = minimumAmount
    this.maximumAmount = maximumAmount
    this.refundWindowDays = refundWindowDays
    this.refundResultReviewAfterMinutes = refundResultReviewAfterMinutes
    this.settlementFeeBasisPoints = settlementFeeBasisPoints
    this.settlementFixedFeeAmount = settlementFixedFeeAmount
    this.settlementFeeRoundingMode = settlementFeeRoundingMode.trim().uppercase()
    this.settlementResultReviewAfterMinutes = settlementResultReviewAfterMinutes
    this.retiredAt = changedAt.takeIf { status == MerchantChannelConfigurationStatus.RETIRED }
}
