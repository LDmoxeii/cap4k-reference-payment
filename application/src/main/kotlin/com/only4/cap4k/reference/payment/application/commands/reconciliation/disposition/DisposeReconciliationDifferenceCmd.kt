package com.only4.cap4k.reference.payment.application.commands.reconciliation.disposition

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.reference.payment.application.errors.ReconciliationBatchNotFoundException
import com.only4.cap4k.reference.payment.domain._share.meta.payment.SPayment
import com.only4.cap4k.reference.payment.domain._share.meta.reconciliation_batch.SReconciliationBatch
import com.only4.cap4k.reference.payment.domain._share.meta.refund.SRefund
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatch
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationBatchId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationConfirmationFactCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationDispositionCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.ReconciliationItem
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.appendDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.recalculateCompletion
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.DispositionAuthorization
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionConclusion
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationDispositionStatus
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.SettlementImpact
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@DesignBlockMetadata(
    tag = "command",
    name = "DisposeReconciliationDifference",
    packageName = "reconciliation.disposition",
    description = "Authorize and append a disposition, optionally forming a separate confirmation fact",
    aggregates = ["ReconciliationBatch"],
    family = "command"
)
object DisposeReconciliationDifferenceCmd {

    @Service
    class Handler : CommandHandler<Request, Response> {
        /**
         * 所有处置尝试都进入 append-only 审计：未授权请求写入 DENIED 记录，授权结论才可能解决差异。
         * 需要形成 confirmation 时，先从 Payment/Refund 弱引用推导 merchant/channel；只有没有弱引用时才接受
         * 显式归属，且任何冲突都拒绝，避免人工处置把资金事实归到错误商户或渠道。
         */
        override fun handle(command: Request): Response {
            require(command.operatorIdentity.isNotBlank()) { "操作员身份不能为空" }
            require(command.evidence.isNotBlank()) { "证据不能为空" }
            val batch = Mediator.repositories.findOne(
                SReconciliationBatch.predicateById(ReconciliationBatchId.parse(command.batchId))
            ) ?: throw ReconciliationBatchNotFoundException(command.batchId)
            val item = batch.reconciliationRuns.asSequence()
                .flatMap { it.reconciliationItems.asSequence() }
                .firstOrNull { it.id.toString() == command.itemId }
                ?: throw IllegalArgumentException("对账批次 ${command.batchId} 中未找到差异项 ${command.itemId}")
            val disposedAt = LocalDateTime.ofInstant(command.disposedAt, ZoneOffset.UTC)
            val authorized = command.operatorRole.trim().uppercase() == AUTHORIZED_OPERATOR_ROLE
            val conclusion = if (authorized) enumValue<ReconciliationDispositionConclusion>(command.conclusion, "conclusion") else null
            val impact = enumValue<SettlementImpact>(command.settlementImpact, "settlementImpact")

            val confirmation = if (authorized && conclusion == ReconciliationDispositionConclusion.CONFIRM_PLATFORM_FACT) {
                confirmationFor(batch, item, command, disposedAt)
            } else null
            val disposition = batch.appendDisposition(
                differenceIdentity = item.differenceIdentity,
                creation = ReconciliationDispositionCreation(
                    operatorIdentity = command.operatorIdentity.trim(),
                    operatorRole = command.operatorRole.trim(),
                    authorizationResult = if (authorized) DispositionAuthorization.AUTHORIZED else DispositionAuthorization.DENIED,
                    status = if (authorized) ReconciliationDispositionStatus.APPLIED else ReconciliationDispositionStatus.REJECTED,
                    conclusion = conclusion,
                    settlementImpact = impact,
                    evidence = command.evidence.trim(),
                    followUp = command.followUp?.trim()?.takeIf { it.isNotBlank() },
                    disposedAt = disposedAt,
                ),
                confirmation = confirmation,
            )
            batch.recalculateCompletion(disposedAt)
            val confirmationFact = item.reconciliationConfirmationFacts.lastOrNull()
                ?.takeIf { confirmation != null && it.sourceDifferenceIdentity == item.differenceIdentity }

            return Response(
                dispositionId = disposition.id.toString(),
                authorization = disposition.authorizationResult.name,
                status = disposition.status.name,
                confirmationFactId = confirmationFact?.id?.toString(),
                batchStatus = batch.status.name,
                settlementBlocked = batch.settlementBlocked,
                blockingReason = batch.blockingReason,
            )
        }

        private fun confirmationFor(
            batch: ReconciliationBatch,
            item: ReconciliationItem,
            command: Request,
            disposedAt: LocalDateTime,
        ): ReconciliationConfirmationFactCreation {
            val amount = item.channelAmount ?: throw IllegalArgumentException("确认事实缺少渠道金额证据")
            val currency = item.channelCurrency ?: throw IllegalArgumentException("确认事实缺少渠道币种证据")
            val externalIdentity = item.channelTransactionIdentity
                ?: throw IllegalArgumentException("确认事实缺少渠道交易身份")
            val attribution = resolveAttribution(batch, item, command)
            return ReconciliationConfirmationFactCreation(
                sourceDifferenceIdentity = item.differenceIdentity,
                merchantId = attribution.merchantId,
                channelId = attribution.channelId,
                operatorIdentity = command.operatorIdentity.trim(),
                confirmationReason = command.followUp?.trim()?.takeIf { it.isNotBlank() }
                    ?: "已授权的对账差异处置",
                evidence = command.evidence.trim(),
                transactionKind = item.transactionKind,
                amount = amount,
                currency = currency,
                externalTransactionIdentity = externalIdentity,
                paymentId = item.paymentId,
                refundId = item.refundId,
                confirmedAt = disposedAt,
            )
        }

        private fun resolveAttribution(
            batch: ReconciliationBatch,
            item: ReconciliationItem,
            command: Request,
        ): ConfirmationAttribution {
            var merchantId: String? = null
            var channelId: String? = null

            item.paymentId?.let { rawPaymentId ->
                val payment = Mediator.repositories.findOne(
                    SPayment.predicateById(PaymentId.parse(rawPaymentId))
                ) ?: throw IllegalArgumentException("确认事实引用的支付单 $rawPaymentId 不存在")
                merchantId = payment.merchantId
                item.paymentAttemptId?.let { rawAttemptId ->
                    val attempt = payment.attempts.firstOrNull { it.id.toString() == rawAttemptId }
                        ?: throw IllegalArgumentException(
                            "确认事实引用的支付尝试 $rawAttemptId 不属于支付单 $rawPaymentId"
                        )
                    channelId = attempt.channelId
                }
            }

            item.refundId?.let { rawRefundId ->
                val refund = Mediator.repositories.findOne(
                    SRefund.predicateById(RefundId.parse(rawRefundId))
                ) ?: throw IllegalArgumentException("确认事实引用的退款单 $rawRefundId 不存在")
                item.paymentId?.let { referencedPaymentId ->
                    require(refund.paymentId.toString() == referencedPaymentId) {
                        "确认事实引用的退款单 $rawRefundId 不属于支付单 $referencedPaymentId"
                    }
                }
                item.refundAttemptId?.let { rawAttemptId ->
                    require(refund.attempts.any { it.id.toString() == rawAttemptId }) {
                        "确认事实引用的退款尝试 $rawAttemptId 不属于退款单 $rawRefundId"
                    }
                }
                merchantId?.let { require(it == refund.merchantId) { "确认事实的弱引用对商户归属存在冲突" } }
                channelId?.let { require(it == refund.channelId) { "确认事实的弱引用对渠道归属存在冲突" } }
                merchantId = refund.merchantId
                channelId = refund.channelId
            }

            val explicitMerchantId = command.merchantId?.trim()?.takeIf { it.isNotBlank() }
            val explicitChannelId = command.channelId?.trim()?.takeIf { it.isNotBlank() }
            if (merchantId == null) {
                merchantId = explicitMerchantId
                    ?: throw IllegalArgumentException("弱引用无法确定商户时必须显式提供 merchantId")
            } else {
                explicitMerchantId?.let {
                    require(it == merchantId) { "显式商户与弱引用归属不一致" }
                }
            }
            if (channelId == null) {
                channelId = explicitChannelId
                    ?: throw IllegalArgumentException("弱引用无法确定渠道时必须显式提供 channelId")
            } else {
                explicitChannelId?.let {
                    require(it == channelId) { "显式渠道与弱引用归属不一致" }
                }
            }

            require(channelId == batch.channelId) { "确认事实的渠道不属于当前对账批次" }
            return ConfirmationAttribution(
                merchantId = requireNotNull(merchantId),
                channelId = requireNotNull(channelId),
            )
        }
    }

    private data class ConfirmationAttribution(
        val merchantId: String,
        val channelId: String,
    )

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
    ) : Command<Response>

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

    private const val AUTHORIZED_OPERATOR_ROLE = "RECONCILIATION_OPERATOR"

    private inline fun <reified E : Enum<E>> enumValue(value: String, field: String): E =
        try {
            enumValueOf<E>(value.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("不支持的 $field 参数值：$value")
        }
}
