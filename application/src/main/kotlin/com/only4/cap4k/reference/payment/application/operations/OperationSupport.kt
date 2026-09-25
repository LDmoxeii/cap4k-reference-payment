package com.only4.cap4k.reference.payment.application.operations

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.errors.OperationNotFoundException
import com.only4.cap4k.reference.payment.application.errors.OperationResourceNotFoundException
import com.only4.cap4k.reference.payment.application.errors.PaymentConflictException
import com.only4.cap4k.reference.payment.application.errors.ResourceNotReadyException
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.contract.common.ApiError
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.OperationAcceptanceStatus
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.contract.common.OperationStatus
import com.only4.cap4k.reference.payment.contract.common.OperationView
import com.only4.cap4k.reference.payment.contract.common.ReadAfter
import com.only4.cap4k.reference.payment.contract.common.ReadAfterMode
import com.only4.cap4k.reference.payment.contract.common.ResourceRef
import com.only4.cap4k.reference.payment.domain._share.meta.operation.SOperation
import com.only4.cap4k.reference.payment.domain.aggregates.operation.Operation
import com.only4.cap4k.reference.payment.domain.aggregates.operation.OperationId
import com.only4.cap4k.reference.payment.domain.aggregates.operation.factory.OperationFactory
import com.only4.cap4k.reference.payment.domain.aggregates.operation.fail
import com.only4.cap4k.reference.payment.domain.aggregates.operation.markProcessing
import com.only4.cap4k.reference.payment.domain.aggregates.operation.requireReview
import com.only4.cap4k.reference.payment.domain.aggregates.operation.succeed
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/**
 * Stores one accepted command operation in the same CAP4K Unit of Work as the business change.
 * Callers validate all synchronously rejectable input before asking for replay or acceptance.
 */
@Service
class OperationSupport(
    private val clock: Clock,
    private val policy: ReferencePolicyService,
    private val json: ObjectMapper,
) {
    fun canonicalHash(vararg fields: String?): String {
        val canonical = buildString {
            fields.forEach { field ->
                val value = field.orEmpty()
                append(value.length).append(':').append(value).append('|')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun replayOrNull(
        merchantId: String,
        commandType: String,
        idempotencyKey: String,
        canonicalRequestHash: String,
    ): Operation? {
        val operation = Mediator.repositories.findOne(
            SOperation.predicate { schema ->
                (schema.merchantId eq merchantId) and
                    (schema.commandType eq commandType) and
                    (schema.idempotencyKey eq idempotencyKey)
            },
        ) ?: return null
        if (operation.canonicalRequestHash != canonicalRequestHash) {
            throw PaymentConflictException(
                code = "IDEMPOTENCY_CONFLICT",
                message = "幂等键已绑定到内容不同的命令",
                details = mapOf("operationId" to operation.id.toString(), "commandType" to commandType),
            )
        }
        return operation
    }

    fun accept(
        merchantId: String,
        commandType: String,
        idempotencyKey: String,
        canonicalRequestHash: String,
        resourceType: String,
        resourceId: String,
        resourceUrl: String?,
    ): OperationReceipt {
        require(!resourceUrl.isNullOrBlank()) { "READ_ONCE requires resourceUrl" }
        val acceptedAt = LocalDateTime.now(clock)
        val operation = Mediator.factories.create<OperationFactory.Payload, Operation>(
            OperationFactory.Payload(
                merchantId = merchantId,
                commandType = commandType,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = canonicalRequestHash,
                resourceType = resourceType,
                resourceId = resourceId,
                status = OperationStatus.SUCCEEDED.name,
                finality = Finality.FINAL.name,
                readAfterMode = ReadAfterMode.READ_ONCE.name,
                readAfterResourceUrl = resourceUrl,
                acceptedAt = acceptedAt,
                completedAt = acceptedAt,
            ),
        )
        return receipt(operation, replay = false)
    }

    /** Must be called inside the business command UoW, after synchronous rejections are ruled out. */
    fun acceptPoll(
        merchantId: String,
        commandType: String,
        idempotencyKey: String,
        canonicalRequestHash: String,
        resourceType: String,
        resourceId: String,
        resourceUrl: String? = null,
    ): OperationReceipt {
        val operation = Mediator.factories.create<OperationFactory.Payload, Operation>(
            OperationFactory.Payload(
                merchantId = merchantId,
                commandType = commandType,
                idempotencyKey = idempotencyKey,
                canonicalRequestHash = canonicalRequestHash,
                resourceType = resourceType,
                resourceId = resourceId,
                status = OperationStatus.ACCEPTED.name,
                finality = Finality.NON_FINAL.name,
                readAfterMode = ReadAfterMode.POLL.name,
                readAfterResourceUrl = resourceUrl,
                acceptedAt = LocalDateTime.now(clock),
                completedAt = null,
                retryAfterMs = policy.current().operationPollRetryAfterMs.toLong(),
            ),
        )
        return receipt(operation, replay = false)
    }

    /** Transitions are intended for command/UoW scope; they never infer business failure from observation time. */
    fun markProcessing(operationId: OperationId) = find(operationId).markProcessing(LocalDateTime.now(clock))

    fun succeed(operationId: OperationId, resourceUrl: String? = null, result: Map<String, Any?>? = null) {
        val operation = find(operationId)
        operation.succeed(
            at = LocalDateTime.now(clock),
            resourceUrl = resourceUrl ?: operation.readAfterResourceUrl,
            resultJson = result?.let { json.writeValueAsString(it.toSortedMap()) },
        )
    }

    fun fail(operationId: OperationId, error: ApiError) {
        require(error.code.isNotBlank()) { "FAILED requires a stable error code" }
        find(operationId).fail(
            at = LocalDateTime.now(clock),
            code = error.code,
            message = error.message,
            detailsJson = json.writeValueAsString(error.details.toSortedMap()),
            correlationId = error.correlationId,
            retryable = error.retryable,
        )
    }

    fun requireReview(operationId: OperationId, reviewId: String) =
        find(operationId).requireReview(LocalDateTime.now(clock), reviewId)

    /** A temporarily missing projection is a read error, not an Operation transition. */
    fun requireResourceReady(operationId: OperationId, ready: Boolean) {
        if (!ready) {
            val operation = find(operationId)
            throw ResourceNotReadyException(operation.id.toString(), operation.retryAfterMs)
        }
    }

    fun receipt(operation: Operation, replay: Boolean): OperationReceipt = OperationReceipt(
        operationId = operation.id.toString(),
        commandType = operation.commandType,
        resource = ResourceRef(operation.resourceType, operation.resourceId),
        acceptanceStatus = if (replay) OperationAcceptanceStatus.ALREADY_ACCEPTED else OperationAcceptanceStatus.ACCEPTED,
        acceptedAt = operation.acceptedAt.toInstant(ZoneOffset.UTC),
        idempotentReplay = replay,
        correlationId = operation.id.toString(),
        readAfter = ReadAfter(
            mode = enumValueOf(operation.readAfterMode),
            resourceUrl = operation.readAfterResourceUrl,
            retryAfterMs = operation.retryAfterMs,
            operationUrl = operationUrl(operation),
        ),
    )

    fun get(operationId: OperationId): OperationView {
        return view(find(operationId))
    }

    /** Resolves the reference fixture resource without treating projection lag as business absence. */
    fun getReadyResource(resourceType: String, resourceId: String): OperationView {
        val operation = Mediator.repositories.findOne(
            SOperation.predicate { schema ->
                (schema.resourceType eq resourceType) and (schema.resourceId eq resourceId)
            },
        ) ?: throw OperationResourceNotFoundException(resourceType, resourceId)
        if (operation.status != OperationStatus.SUCCEEDED.name) {
            throw ResourceNotReadyException(operation.id.toString(), operation.retryAfterMs)
        }
        return view(operation)
    }

    private fun view(operation: Operation): OperationView = OperationView(
            operationId = operation.id.toString(),
            merchantId = operation.merchantId,
            commandType = operation.commandType,
            resource = ResourceRef(operation.resourceType, operation.resourceId),
            status = enumValueOf(operation.status),
            finality = enumValueOf(operation.finality),
            acceptedAt = operation.acceptedAt.toInstant(ZoneOffset.UTC),
            completedAt = operation.completedAt?.toInstant(ZoneOffset.UTC),
            updatedAt = (operation.updatedAt ?: operation.acceptedAt).toInstant(ZoneOffset.UTC),
            resourceUrl = operation.readAfterResourceUrl,
            result = operation.resultJson?.let { json.readValue(it, object : TypeReference<Map<String, Any?>>() {}) },
            error = operation.errorCode?.let { code ->
                ApiError(
                    code = code,
                    message = requireNotNull(operation.errorMessage),
                    details = operation.errorDetailsJson?.let {
                        json.readValue(it, object : TypeReference<Map<String, Any?>>() {})
                    } ?: emptyMap(),
                    correlationId = operation.errorCorrelationId ?: operation.id.toString(),
                    retryable = operation.errorRetryable ?: false,
                )
            },
            reviewId = operation.reviewId,
            readAfter = ReadAfter(
                mode = enumValueOf(operation.readAfterMode),
                resourceUrl = operation.readAfterResourceUrl,
                retryAfterMs = operation.retryAfterMs,
                operationUrl = operationUrl(operation),
            ),
        )

    private fun find(operationId: OperationId): Operation =
        Mediator.repositories.findOne(SOperation.predicateById(operationId))
            ?: throw OperationNotFoundException(operationId)

    private fun operationUrl(operation: Operation): String = "/api/operations/${operation.id}"
}
