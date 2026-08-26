package com.only4.cap4k.reference.payment.contract.endpoints.reconciliation.api

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import java.time.Instant

/**
 * POST /api/reconciliation-batches/{batchId}/reruns
 */
@DesignBlockMetadata(
    tag = "endpoint",
    name = "RerunReconciliationBatchEndpoint",
    packageName = "reconciliation.api",
    description = "POST /api/reconciliation-batches/{batchId}/reruns",
    aggregates = [],
    operationName = "reconciliation.batch.rerun",
    family = "endpoint"
)
object RerunReconciliationBatchEndpoint {
    const val OPERATION_NAME: String = "reconciliation.batch.rerun"

    data class Request(
        /**
         * 批次标识
         */
        val batchId: String,
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
         * 批次标识
         */
        val batchId: String,
        /**
         * 运行标识
         */
        val runId: String?,
        /**
         * 状态
         */
        val status: String,
        /**
         * 是否幂等重放
         */
        val idempotentReplay: Boolean,
        /**
         * 对账单身份
         */
        val statementIdentity: String?,
        /**
         * 对账单版本
         */
        val statementRevision: String?
    )

}
