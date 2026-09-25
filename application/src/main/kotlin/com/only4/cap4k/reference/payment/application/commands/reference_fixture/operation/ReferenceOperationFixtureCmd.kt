package com.only4.cap4k.reference.payment.application.commands.reference_fixture.operation

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.contract.common.ApiError
import com.only4.cap4k.reference.payment.contract.common.OperationReceipt
import com.only4.cap4k.reference.payment.contract.common.OperationView
import com.only4.cap4k.reference.payment.contract.common.ReadAfterMode
import com.only4.cap4k.reference.payment.domain.aggregates.operation.OperationId
import org.springframework.stereotype.Service

/** Reference-only commands. They intentionally use the production Operation aggregate and UoW. */
@DesignBlockMetadata(
    tag = "command",
    name = "CreateReferenceOperationFixture",
    packageName = "reference_fixture.operation",
    description = "Create a deterministic reference operation through the production Operation aggregate",
    aggregates = ["Operation"],
    family = "command",
)
object CreateReferenceOperationFixtureCmd {
    data class Request(
        val merchantId: String,
        val commandType: String,
        val idempotencyKey: String,
        val resourceType: String,
        val resourceId: String,
        val readAfterMode: String,
        val resourceUrl: String?,
    ) : Command<Response>

    data class Response(val receipt: OperationReceipt)

    @Service
    class Handler(private val operations: OperationSupport) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val merchantId = command.merchantId.trim().also { require(it.isNotEmpty()) { "merchantId 不能为空" } }
            val commandType = command.commandType.trim().also { require(it.isNotEmpty()) { "commandType 不能为空" } }
            val key = command.idempotencyKey.trim().also { require(it.isNotEmpty()) { "idempotencyKey 不能为空" } }
            val resourceType = command.resourceType.trim().also { require(it.isNotEmpty()) { "resourceType 不能为空" } }
            val resourceId = command.resourceId.trim().also { require(it.isNotEmpty()) { "resourceId 不能为空" } }
            val mode = runCatching { ReadAfterMode.valueOf(command.readAfterMode.trim().uppercase()) }
                .getOrElse { throw IllegalArgumentException("readAfterMode 只允许 READ_ONCE 或 POLL") }
            val resourceUrl = command.resourceUrl?.trim()?.takeIf(String::isNotEmpty)
                ?: "/api/reference-fixtures/operation-resources/$resourceId"
            val hash = operations.canonicalHash(resourceType, resourceId, mode.name, resourceUrl)
            operations.replayOrNull(merchantId, commandType, key, hash)?.let {
                return Response(operations.receipt(it, replay = true))
            }
            val receipt = when (mode) {
                ReadAfterMode.READ_ONCE -> operations.accept(
                    merchantId, commandType, key, hash, resourceType, resourceId, resourceUrl,
                )
                ReadAfterMode.POLL -> operations.acceptPoll(
                    merchantId, commandType, key, hash, resourceType, resourceId, resourceUrl,
                )
            }
            return Response(receipt)
        }
    }
}

@DesignBlockMetadata(
    tag = "command",
    name = "TransitionReferenceOperationFixture",
    packageName = "reference_fixture.operation",
    description = "Transition a reference operation to an authoritative terminal or review state",
    aggregates = ["Operation"],
    family = "command",
)
object TransitionReferenceOperationFixtureCmd {
    data class Request(
        val operationId: String,
        val outcome: String,
        val resourceUrl: String?,
        val result: Map<String, Any?>?,
        val errorCode: String?,
        val errorMessage: String?,
        val errorDetails: Map<String, Any?>?,
        val retryable: Boolean?,
        val reviewId: String?,
    ) : Command<Response>

    data class Response(val operation: OperationView)

    @Service
    class Handler(private val operations: OperationSupport) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val id = OperationId.parse(command.operationId)
            when (command.outcome.trim().uppercase()) {
                "PROCESSING" -> operations.markProcessing(id)
                "SUCCEEDED" -> operations.succeed(id, command.resourceUrl, command.result)
                "FAILED" -> operations.fail(
                    id,
                    ApiError(
                        code = command.errorCode?.trim()?.takeIf(String::isNotEmpty)
                            ?: throw IllegalArgumentException("FAILED 必须提供 errorCode"),
                        message = command.errorMessage?.trim()?.takeIf(String::isNotEmpty)
                            ?: throw IllegalArgumentException("FAILED 必须提供 errorMessage"),
                        details = command.errorDetails ?: emptyMap(),
                        correlationId = command.operationId,
                        retryable = command.retryable ?: false,
                    ),
                )
                "REVIEW_REQUIRED" -> operations.requireReview(
                    id,
                    command.reviewId?.trim()?.takeIf(String::isNotEmpty)
                        ?: throw IllegalArgumentException("REVIEW_REQUIRED 必须提供 reviewId"),
                )
                else -> throw IllegalArgumentException(
                    "outcome 只允许 PROCESSING、SUCCEEDED、FAILED 或 REVIEW_REQUIRED",
                )
            }
            return Response(operations.get(id))
        }
    }
}
