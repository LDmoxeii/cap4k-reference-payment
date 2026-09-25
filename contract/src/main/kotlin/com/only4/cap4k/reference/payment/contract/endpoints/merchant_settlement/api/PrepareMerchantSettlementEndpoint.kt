package com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import java.time.Instant

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
         * 币种
         */
        val currency: String,
        /**
         * 结算周期。scope 仅由 merchantId + currency + 此 period 定义。
         */
        val settlementPeriod: SettlementPeriod,
        val idempotencyKey: String,
    ) : EndpointRequest<Response> {
        data class SettlementPeriod(
            /** 包含的 RFC3339 instant。 */
            val start: Instant,
            /** 不包含的 RFC3339 instant。 */
            val end: Instant,
            /** 业务日边界时区；reference 默认 Asia/Shanghai。 */
            val timezone: String = "Asia/Shanghai",
        )
    }

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
        val grossMoney: Money,
        /**
         * 退款毛金额
         */
        val refundMoney: Money,
        /**
         * 费用总额
         */
        val feeMoney: Money,
        /**
         * 调整总额
         */
        val adjustmentMoney: Money,
        /**
         * 净金额
         */
        val netMoney: Money,
        val receipt: OperationReceipt,
    )

}
