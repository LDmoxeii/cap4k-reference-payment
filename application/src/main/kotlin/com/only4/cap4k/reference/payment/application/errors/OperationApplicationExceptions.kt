package com.only4.cap4k.reference.payment.application.errors

import com.only4.cap4k.reference.payment.domain.aggregates.operation.OperationId

class OperationNotFoundException(operationId: OperationId) : PaymentApplicationException(
    code = "NOT_FOUND",
    message = "未找到 Operation $operationId",
    details = mapOf("operationId" to operationId.toString()),
)

class OperationResourceNotFoundException(resourceType: String, resourceId: String) : PaymentApplicationException(
    code = "NOT_FOUND",
    message = "未找到 Operation 关联资源",
    details = mapOf("resourceType" to resourceType, "resourceId" to resourceId),
)

class ResourceNotReadyException(operationId: String, retryAfterMs: Long) : PaymentApplicationException(
    code = "RESOURCE_NOT_READY",
    message = "已受理 Operation 的资源暂不可读，请稍后重试",
    details = mapOf("operationId" to operationId, "retryAfterMs" to retryAfterMs),
)
