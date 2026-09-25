package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import java.time.Instant

/** POST /api/payments/{paymentId}/reviews/{reviewId}/decisions */
@DesignBlockMetadata(tag = "endpoint", name = "AdjudicatePaymentReviewEndpoint", packageName = "payment.api", description = "POST /api/payments/{paymentId}/reviews/{reviewId}/decisions", aggregates = [], operationName = "payment.review.adjudicate", family = "endpoint")
object AdjudicatePaymentReviewEndpoint {
    const val OPERATION_NAME = "payment.review.adjudicate"
    data class Request(
        /**
         * 支付标识
         */
        val paymentId: String,
        val merchantId: String,
        val idempotencyKey: String,
        /**
         * 复核标识
         */
        val reviewId: String,
        /**
         * 决策身份
         */
        val decisionIdentity: String,
        /**
         * 决策
         */
        val decision: String,
        /**
         * 原因
         */
        val reason: String,
        /**
         * 证据
         */
        val evidence: String,
        /**
         * 资格影响
         */
        val eligibilityImpact: String,
        /**
         * 补救引用
         */
        val remediationReference: String?,
    ) : EndpointRequest<Response>
    data class Response(
        /**
         * 支付状态
         */
        val paymentStatus: String,
        /**
         * 复核状态
         */
        val reviewStatus: String,
        /**
         * 决策数量
         */
        val decisionCount: Int,
        /**
         * 是否符合结算条件
         */
        val settlementEligible: Boolean,
        /**
         * 通知意图状态
         */
        val notificationIntentState: String?,
        val decisionId: String,
        val decisionIdentity: String,
        val decision: String,
        /** 由可信 reference actor context 解析并持久化的责任人。 */
        val actorId: String,
        val reason: String,
        val evidence: String,
        val decidedAt: Instant,
        val receipt: OperationReceipt,
    )
}
