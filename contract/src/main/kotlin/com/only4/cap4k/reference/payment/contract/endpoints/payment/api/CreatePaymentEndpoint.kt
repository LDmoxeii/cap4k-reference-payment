package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.Money
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt

/**
 * POST /api/payments
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreatePaymentEndpoint",
    packageName = "payment.api",
    description = "POST /api/payments",
    aggregates = [],
    operationName = "payment.create",
    family = "endpoint"
)
object CreatePaymentEndpoint {
    const val OPERATION_NAME: String = "payment.create"

    data class Request(
        /**
         * 商户标识
         */
        val merchantId: String,
        /**
         * 商户订单号
         */
        val merchantOrderNumber: String,
        /**
         * 幂等键
         */
        val idempotencyKey: String,
        /**
         * 金额
         */
        val money: Money,
        /**
         * 支付方式
         */
        val paymentMethod: String,
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 支付标识
         */
        val paymentId: String,
        /**
         * 状态
         */
        val status: String,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean,
        /**
         * 拒绝代码
         */
        val rejectionCode: String?,
        /**
         * 拒绝摘要
         */
        val rejectionSummary: String?,
        /** Unified command acceptance receipt. */
        val receipt: OperationReceipt,
    )

}
