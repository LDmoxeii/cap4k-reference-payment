package com.only4.cap4k.reference.payment.contract.endpoints.payment.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
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
         * 操作员身份
         */
        val operatorIdentity: String,
        /**
         * 操作员角色
         */
        val operatorRole: String,
        /**
         * 授权材料
         */
        val authorizationMaterial: String,
        /**
         * 原因
         */
        val reason: String,
        /**
         * 证据
         */
        val evidence: String,
        /**
         * 决策时间
         */
        val decidedAt: Instant,
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
    )
}
