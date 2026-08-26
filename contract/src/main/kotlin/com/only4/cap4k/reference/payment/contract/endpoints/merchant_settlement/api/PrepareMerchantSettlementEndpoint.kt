package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * POST /api/merchant-settlements
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "PrepareMerchantSettlementEndpoint",
    packageName = "merchant_settlement.api",
    description = "POST /api/merchant-settlements",
    aggregates = [],
    operationName = "merchant-settlement.prepare",
    family = "endpoint"
)
object PrepareMerchantSettlementEndpoint {
    const val OPERATION_NAME: String = "merchant-settlement.prepare"

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 渠道标识
         */
        val channelId: String,
        /**
         * 币种
         */
        val currency: String,
        /**
         * 结算日期
         */
        val settlementDate: LocalDate,
        /**
         * 请求操作人
         */
        val requestedBy: String,
        /**
         * 请求时间
         */
        val requestedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 结算标识
         */
        val settlementId: String?,
        /**
         * 状态
         */
        val status: String?,
        /**
         * 创建时间
         */
        val created: Boolean,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean,
        /**
         * 是否无操作
         */
        val noOp: Boolean,
        /**
         * 符合数量
         */
        val eligibleCount: Int,
        /**
         * 排除数量
         */
        val excludedCount: Int,
        /**
         * 阻塞摘要
         */
        val blockerSummary: String?,
        /**
         * 支付毛金额
         */
        val paymentGrossAmount: BigDecimal,
        /**
         * 退款毛金额
         */
        val refundGrossAmount: BigDecimal,
        /**
         * 费用总额
         */
        val feeTotalAmount: BigDecimal,
        /**
         * 调整总额
         */
        val adjustmentTotalAmount: BigDecimal,
        /**
         * 净金额
         */
        val netAmount: BigDecimal
    )

}
