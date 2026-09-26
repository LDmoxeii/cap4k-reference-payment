package com.only4.cap4k.reference.payment.adapter.endpoints.merchant_settlement

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.merchant_settlement.execution.StartMerchantSettlementExecutionCmd
import com.only4.cap4k.reference.payment.adapter.application.queries.merchant_settlement.read.MerchantSettlementContractStatus
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.MerchantSettlementId
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_settlement.enums.MerchantSettlementStatus
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_settlement.api.StartMerchantSettlementExecutionEndpoint
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class StartMerchantSettlementExecutionEndpointHandler(
    private val clock: Clock,
) : EndpointHandler<StartMerchantSettlementExecutionEndpoint.Request, StartMerchantSettlementExecutionEndpoint.Response> {
    override fun handle(request: StartMerchantSettlementExecutionEndpoint.Request): StartMerchantSettlementExecutionEndpoint.Response {
        val response = Mediator.commands.send(
            StartMerchantSettlementExecutionCmd.Request(
                merchantSettlementId = MerchantSettlementId.parse(request.settlementId),
                merchantId = request.merchantId,
                executionId = request.executionId,
                operatorIdentity = REFERENCE_SYSTEM_ACTOR_ID,
                operatorRole = REFERENCE_SETTLEMENT_EXECUTOR_ROLE,
                executionChannelId = request.executionChannelId,
                idempotencyKey = request.idempotencyKey,
                requestedAt = Instant.now(clock),
            )
        )
        return StartMerchantSettlementExecutionEndpoint.Response(
            settlementId = response.merchantSettlementId.toString(),
            executionId = response.executionId,
            attemptId = response.attemptId,
            executionGroupIdentity = response.executionGroupIdentity,
            requestIdentity = response.requestIdentity,
            status = MerchantSettlementContractStatus.publicStatus(MerchantSettlementStatus.valueOf(response.status)),
            providerAccepted = response.providerAccepted,
            diagnosticSummary = response.diagnosticSummary,
            executorScript = response.executorScript,
            executorObservation = response.executorObservation,
            actorId = REFERENCE_SYSTEM_ACTOR_ID,
            receipt = response.receipt,
        )
    }

    private companion object {
        const val REFERENCE_SYSTEM_ACTOR_ID = "reference-system"
        const val REFERENCE_SETTLEMENT_EXECUTOR_ROLE = "SETTLEMENT_OPERATOR"
    }
}
