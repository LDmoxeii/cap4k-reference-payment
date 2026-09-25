package com.only4.cap4k.reference.payment.contract.common

import java.time.Instant

/** Unified external Money representation. amountMinor is always a decimal integer string. */
data class Money(
    val currency: String,
    val amountMinor: String,
)

enum class Finality {
    NON_FINAL,
    FINAL,
    REVIEW_REQUIRED,
}

/** Stable public vocabulary for payment and refund channel-result receipts. */
enum class ChannelResultDisposition {
    ACCEPTED,
    DUPLICATE,
    REJECTED_INVALID,
    UNKNOWN_REFERENCE,
    LATE,
    CONFLICTING,
}

data class ResourceRef(
    val resourceType: String,
    val resourceId: String,
)

enum class ReadAfterMode {
    READ_ONCE,
    POLL,
}

data class ReadAfter(
    val mode: ReadAfterMode,
    val resourceUrl: String?,
    val retryAfterMs: Long,
    val operationUrl: String,
)

enum class OperationAcceptanceStatus {
    ACCEPTED,
    ALREADY_ACCEPTED,
}

enum class OperationStatus {
    ACCEPTED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REVIEW_REQUIRED,
}

data class OperationReceipt(
    val operationId: String,
    val commandType: String,
    val resource: ResourceRef,
    val acceptanceStatus: OperationAcceptanceStatus,
    val acceptedAt: Instant,
    val idempotentReplay: Boolean,
    val correlationId: String,
    val readAfter: ReadAfter,
)

data class OperationView(
    val operationId: String,
    val merchantId: String,
    val commandType: String,
    val resource: ResourceRef,
    val status: OperationStatus,
    val finality: Finality,
    val acceptedAt: Instant,
    val completedAt: Instant?,
    val readAfter: ReadAfter,
    val updatedAt: Instant,
    val resourceUrl: String?,
    val result: Map<String, Any?>? = null,
    val error: ApiError? = null,
    val reviewId: String? = null,
)

data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, Any?> = emptyMap(),
    val correlationId: String,
    val retryable: Boolean,
)

data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
    val pageSize: Int,
)
