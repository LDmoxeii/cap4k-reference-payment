package com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/**
 * POST /api/reconciliation-items/{itemId}/dispositions
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "DisposeReconciliationDifferenceEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-items/{itemId}/dispositions",
    aggregates = [],
    operationName = "reconciliation.difference.dispose",
    family = "endpoint"
)
object DisposeReconciliationDifferenceEndpoint {
    const val OPERATION_NAME: String = "reconciliation.difference.dispose"

    data class Request(
        /**
         * 批次标识
         */
        val batchId: String,
        /**
         * 条目标识
         */
        val itemId: String,
        /**
         * 商户标识
         */
        val merchantId: String?,
        /**
         * 渠道标识
         */
        val channelId: String?,
        /**
         * 操作员身份
         */
        val operatorIdentity: String,
        /**
         * 操作员角色
         */
        val operatorRole: String,
        /**
         * 结论
         */
        val conclusion: String,
        /**
         * 结算影响
         */
        val settlementImpact: String,
        /**
         * 证据
         */
        val evidence: String,
        /**
         * 后续动作
         */
        val followUp: String?,
        /**
         * 处置时间
         */
        val disposedAt: Instant
    ) : EndpointRequest<Response>

    data class Response(
        /**
         * 处置标识
         */
        val dispositionId: String,
        /**
         * 授权结果
         */
        val authorization: String,
        /**
         * 状态
         */
        val status: String,
        /**
         * 确认事实标识
         */
        val confirmationFactId: String?,
        /**
         * 批次状态
         */
        val batchStatus: String,
        /**
         * 是否阻塞结算
         */
        val settlementBlocked: Boolean,
        /**
         * 原因
         */
        val blockingReason: String?
    )

}
