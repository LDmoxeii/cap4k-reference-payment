package com.only4.cap4k.reference.payment.adapter.endpoints.reference_fixture

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.adapter.endpoints.toDomainAmount
import com.only4.cap4k.reference.payment.adapter.reference.ReferenceBillReadScriptRegistry
import com.only4.cap4k.reference.payment.application.commands.reconciliation.bill.ReceiveAuthoritativeBillAvailableSignalCmd
import com.only4.cap4k.reference.payment.application.commands.reconciliation.bill.RegisterAuthoritativeBillRevisionCmd
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.RegisterReferenceAuthoritativeBillRevisionEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.reference_fixture.api.SignalReferenceAuthoritativeBillAvailableEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.BillRevisionCreation
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.BillRevisionRecordCreation
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import org.springframework.stereotype.Component

@Component
class RegisterReferenceAuthoritativeBillRevisionEndpointHandler(
    private val billReadScripts: ReferenceBillReadScriptRegistry,
) : EndpointHandler<RegisterReferenceAuthoritativeBillRevisionEndpoint.Request, RegisterReferenceAuthoritativeBillRevisionEndpoint.Response> {
    override fun handle(request: RegisterReferenceAuthoritativeBillRevisionEndpoint.Request): RegisterReferenceAuthoritativeBillRevisionEndpoint.Response {
        val channelId = required(request.channelId, "渠道标识")
        val billIdentity = required(request.billIdentity, "账单身份")
        val currency = required(request.currency, "账单币种").uppercase()
        val response = Mediator.commands.send(
            RegisterAuthoritativeBillRevisionCmd.Request(
                channelId = channelId,
                billIdentity = billIdentity,
                businessDate = request.businessDate ?: throw IllegalArgumentException("账单业务日不能为空"),
                currency = currency,
                businessTimezone = required(request.businessTimezone, "账单业务时区"),
                revision = BillRevisionCreation(
                    revision = required(request.revision, "账单 revision"),
                    completeness = statementCompleteness(request.completeness, "账单完整性"),
                    rawEvidence = required(request.rawEvidence, "账单原始证据"),
                    payloadFingerprint = required(request.payloadFingerprint, "账单正文指纹"),
                    publishedAt = request.publishedAt ?: throw IllegalArgumentException("账单发布时间不能为空"),
                    records = request.records.orEmpty().map { record ->
                        val money = record.money ?: throw IllegalArgumentException("账单行金额不能为空")
                        BillRevisionRecordCreation(
                            recordIdentity = required(record.recordIdentity, "账单行身份"),
                            channelTransactionIdentity = required(record.channelTransactionIdentity, "渠道交易身份"),
                            transactionKind = transactionKind(record.transactionKind, "账单行交易类型"),
                            amount = money.toDomainAmount(),
                            currency = money.currency,
                            rawStatus = required(record.rawStatus, "账单行状态"),
                            occurredAt = record.occurredAt,
                            receivedAt = record.receivedAt ?: throw IllegalArgumentException("账单行接收时间不能为空"),
                            rawEvidence = required(record.rawEvidence, "账单行原始证据"),
                        )
                    },
                ),
            ),
        )
        request.unavailableReadCount?.let { count -> billReadScripts.scriptUnavailableReads(channelId, billIdentity, count) }
        return RegisterReferenceAuthoritativeBillRevisionEndpoint.Response(
            billId = response.billId,
            billIdentity = response.billIdentity,
            revision = response.revision,
            currentRevision = response.currentRevision,
            idempotentReplay = response.idempotentReplay,
            becameCurrent = response.becameCurrent,
        )
    }

    private fun required(value: String?, label: String): String = value?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("${label}不能为空")

    private fun statementCompleteness(value: String?, label: String): StatementCompleteness = try {
        StatementCompleteness.valueOf(required(value, label).uppercase())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("不支持的$label")
    }

    private fun transactionKind(value: String?, label: String): ReconciliationTransactionKind = try {
        ReconciliationTransactionKind.valueOf(required(value, label).uppercase())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("不支持的$label")
    }
}

@Component
class SignalReferenceAuthoritativeBillAvailableEndpointHandler : EndpointHandler<SignalReferenceAuthoritativeBillAvailableEndpoint.Request, SignalReferenceAuthoritativeBillAvailableEndpoint.Response> {
    override fun handle(request: SignalReferenceAuthoritativeBillAvailableEndpoint.Request): SignalReferenceAuthoritativeBillAvailableEndpoint.Response {
        val response = Mediator.commands.send(
            ReceiveAuthoritativeBillAvailableSignalCmd.Request(
                channelId = required(request.channelId, "渠道标识"),
                billIdentity = required(request.billIdentity, "账单身份"),
                businessDate = request.businessDate ?: throw IllegalArgumentException("账单业务日不能为空"),
                currency = required(request.currency, "账单币种"),
                businessTimezone = required(request.businessTimezone, "账单业务时区"),
                signalIdentity = required(request.signalIdentity, "账单可用信号身份"),
                announcedRevision = required(request.announcedRevision, "账单可用信号 revision"),
                publishedAt = request.publishedAt ?: throw IllegalArgumentException("账单信号发布时间不能为空"),
                correlationIdentity = request.correlationIdentity,
                causationIdentity = request.causationIdentity,
            ),
        )
        return SignalReferenceAuthoritativeBillAvailableEndpoint.Response(
            billId = response.billId,
            signalId = response.signalId,
            runId = response.runId,
            runStatus = response.runStatus,
            idempotentReplay = response.idempotentReplay,
            diagnostic = response.diagnostic,
        )
    }

    private fun required(value: String?, label: String): String = value?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("${label}不能为空")
}
