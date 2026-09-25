package com.only4.cap4k.reference.payment.adapter.endpoints.refund

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.contract.ReferenceContractStatusMapper
import com.only4.cap4k.reference.payment.adapter.endpoints.toDomainAmount
import com.only4.cap4k.reference.payment.application.commands.refund.create.RequestRefundCmd
import com.only4.cap4k.reference.payment.contract.endpoints.refund.api.RequestRefundEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import jakarta.persistence.OptimisticLockException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component

@Component
class RequestRefundEndpointHandler : EndpointHandler<RequestRefundEndpoint.Request, RequestRefundEndpoint.Response> {
    override fun handle(request: RequestRefundEndpoint.Request): RequestRefundEndpoint.Response {
        val command = RequestRefundCmd.Request(
            merchantId = request.merchantId,
            idempotencyKey = request.idempotencyKey,
            merchantRefundNumber = request.merchantRefundNo,
            paymentId = PaymentId.parse(request.paymentId),
            amount = request.money.toDomainAmount(),
            currency = request.money.currency,
            reason = request.reason,
        )
        val response = sendWithFreshBudgetDecision(command)
        return RequestRefundEndpoint.Response(
            refundId = response.refundId.toString(),
            status = ReferenceContractStatusMapper.refundStatus(response.status),
            finality = ReferenceContractStatusMapper.refundFinality(response.status),
            idempotentReplay = response.idempotentReplay,
            receipt = response.receipt,
        )
    }

    /**
     * A losing concurrent reservation may fail while the first UoW flushes the Payment version.
     * Retry the whole command in a fresh CAP4K UoW so the public result is decided from the latest
     * authoritative refund budget (`REFUND_BUDGET_EXCEEDED`) and the rolled-back attempt leaves no
     * Refund or Operation residue.
     */
    private fun sendWithFreshBudgetDecision(command: RequestRefundCmd.Request): RequestRefundCmd.Response =
        try {
            Mediator.commands.send(command)
        } catch (error: RuntimeException) {
            if (!error.isOptimisticLockConflict()) throw error
            Mediator.commands.send(command)
        }

    private fun Throwable.isOptimisticLockConflict(): Boolean =
        generateSequence(this) { it.cause }.any {
            it is OptimisticLockException || it is OptimisticLockingFailureException
        }
}
