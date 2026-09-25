package com.only4.cap4k.reference.payment.application.commands.reconciliation.bill

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel.ChannelStatementUnavailableException
import com.only4.cap4k.reference.payment.application.commands.reconciliation.integration.ProcessAvailableChannelStatementCmd
import com.only4.cap4k.reference.payment.domain._share.meta.authoritative_bill.SAuthoritativeBill
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.BillAvailableSignalCreation
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.recordBillAvailableSignal
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.recordReadAttempt
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.springframework.stereotype.Service

/**
 * Receives a provider availability signal.  The signal carries only the identity and revision;
 * the follow-up reconciliation path still pulls the body from the authoritative provider.
 */
@DesignBlockMetadata(
    tag = "command",
    name = "ReceiveAuthoritativeBillAvailableSignal",
    packageName = "reconciliation.bill",
    description = "Record a stable bill-available signal and trigger the existing transactional reconciliation path",
    aggregates = ["AuthoritativeBill", "ReconciliationBatch"],
    family = "command",
)
object ReceiveAuthoritativeBillAvailableSignalCmd {
    @Service
    class Handler(
        private val clock: Clock,
    ) : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val bill = Mediator.repositories.findOne(
                SAuthoritativeBill.predicate { schema ->
                    (schema.channelId eq command.channelId.trim()) and (schema.billIdentity eq command.billIdentity.trim())
                },
            ) ?: throw IllegalArgumentException("未找到权威账单：${command.channelId}/${command.billIdentity}")
            require(bill.currency == command.currency.trim().uppercase()) { "账单可用信号币种与权威账单不一致" }
            require(bill.businessDate == command.businessDate) { "账单可用信号业务日与权威账单不一致" }
            require(bill.businessTimezone == command.businessTimezone.trim()) { "账单可用信号业务时区与权威账单不一致" }

            val now = Instant.now(clock)
            val signal = bill.recordBillAvailableSignal(
                BillAvailableSignalCreation(
                    signalIdentity = command.signalIdentity.trim(),
                    announcedRevision = command.announcedRevision.trim(),
                    publishedAt = command.publishedAt,
                    receivedAt = now,
                ),
            )
            return try {
                val run = Mediator.commands.send(
                    ProcessAvailableChannelStatementCmd.Request(
                        eventIdentity = signal.signalIdentity,
                        channelId = bill.channelId,
                        currency = bill.currency,
                        reconciliationDate = bill.businessDate,
                        statementIdentity = bill.billIdentity,
                        statementRevision = signal.announcedRevision,
                        publishedAt = command.publishedAt,
                        correlationIdentity = command.correlationIdentity,
                        causationIdentity = command.causationIdentity,
                    ),
                )
                bill.recordReadAttempt(signal.signalIdentity, now, diagnostic = null)
                Response(
                    billId = bill.id.toString(),
                    signalId = signal.id.toString(),
                    runId = run.runId,
                    runStatus = run.batchStatus,
                    idempotentReplay = run.idempotentReplay,
                    diagnostic = null,
                )
            } catch (failure: ChannelStatementUnavailableException) {
                // Preserve a stable diagnostic and let the identical signal be retried; the raw
                // provider cause deliberately remains in logs rather than in the business API.
                bill.recordReadAttempt(signal.signalIdentity, now, "权威账单暂不可读，可使用同一 signalIdentity 重试")
                Response(
                    billId = bill.id.toString(),
                    signalId = signal.id.toString(),
                    runId = null,
                    runStatus = "FAILED",
                    idempotentReplay = false,
                    diagnostic = bill.lastFetchDiagnostic,
                )
            }
        }
    }

    data class Request(
        val channelId: String,
        val billIdentity: String,
        val businessDate: LocalDate,
        val currency: String,
        val businessTimezone: String,
        val signalIdentity: String,
        val announcedRevision: String,
        val publishedAt: Instant,
        val correlationIdentity: String? = null,
        val causationIdentity: String? = null,
    ) : Command<Response>

    data class Response(
        val billId: String,
        val signalId: String,
        val runId: String?,
        val runStatus: String,
        val idempotentReplay: Boolean,
        val diagnostic: String?,
    )
}
