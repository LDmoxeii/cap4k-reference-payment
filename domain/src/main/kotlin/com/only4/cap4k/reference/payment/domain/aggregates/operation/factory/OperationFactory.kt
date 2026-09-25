package com.only4.cap4k.reference.payment.domain.aggregates.operation.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.operation.Operation
import com.only4.cap4k.reference.payment.domain.aggregates.operation.requireInitialOperationState
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "Operation",
    name = "OperationFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.operation.factory",
    description = "",
    type = "factory",
    root = false,
)
class OperationFactory : AggregateFactory<OperationFactory.Payload, Operation> {
    override fun create(entityPayload: Payload): Operation {
        entityPayload.requireInitialOperationState()
        return Operation(
            merchantId = entityPayload.merchantId,
            commandType = entityPayload.commandType,
            idempotencyKey = entityPayload.idempotencyKey,
            canonicalRequestHash = entityPayload.canonicalRequestHash,
            resourceType = entityPayload.resourceType,
            resourceId = entityPayload.resourceId,
            status = entityPayload.status,
            finality = entityPayload.finality,
            readAfterMode = entityPayload.readAfterMode,
            readAfterResourceUrl = entityPayload.readAfterResourceUrl,
            retryAfterMs = entityPayload.retryAfterMs,
            acceptedAt = entityPayload.acceptedAt,
            completedAt = entityPayload.completedAt,
        )
    }

    data class Payload(
        val merchantId: String,
        val commandType: String,
        val idempotencyKey: String,
        val canonicalRequestHash: String,
        val resourceType: String,
        val resourceId: String,
        val status: String,
        val finality: String,
        val readAfterMode: String,
        val readAfterResourceUrl: String?,
        val acceptedAt: LocalDateTime,
        val completedAt: LocalDateTime?,
        val retryAfterMs: Long = 0,
    ) : AggregatePayload<Operation>
}
