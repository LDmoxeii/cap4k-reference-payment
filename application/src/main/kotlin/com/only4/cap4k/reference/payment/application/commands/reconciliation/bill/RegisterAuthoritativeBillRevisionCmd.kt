package com.only4.cap4k.reference.payment.application.commands.reconciliation.bill

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.domain.repo.schema.and
import com.only4.cap4k.reference.payment.domain._share.meta.authoritative_bill.SAuthoritativeBill
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBill
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.BillRevisionAppendResult
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.BillRevisionCreation
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.appendRevision
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.factory.AuthoritativeBillFactory
import java.time.LocalDate
import org.springframework.stereotype.Service

/** Provider-facing fixture command.  It stores evidence; it does not create a reconciliation run. */
@DesignBlockMetadata(
    tag = "command",
    name = "RegisterAuthoritativeBillRevision",
    packageName = "reconciliation.bill",
    description = "Persist one immutable provider bill revision and advance only the monotonic current pointer",
    aggregates = ["AuthoritativeBill"],
    family = "command",
)
object RegisterAuthoritativeBillRevisionCmd {
    @Service
    class Handler : CommandHandler<Request, Response> {
        override fun handle(command: Request): Response {
            val channelId = command.channelId.trim()
            val billIdentity = command.billIdentity.trim()
            val currency = command.currency.trim().uppercase()
            val timezone = command.businessTimezone.trim()
            require(channelId.isNotBlank()) { "渠道标识不能为空" }
            require(billIdentity.isNotBlank()) { "账单身份不能为空" }
            require(currency.isNotBlank()) { "账单币种不能为空" }
            require(timezone.isNotBlank()) { "账单业务时区不能为空" }

            val bill = Mediator.repositories.findOne(
                SAuthoritativeBill.predicate { schema ->
                    (schema.channelId eq channelId) and (schema.billIdentity eq billIdentity)
                },
            ) ?: Mediator.factories.create<AuthoritativeBillFactory.Payload, AuthoritativeBill>(
                AuthoritativeBillFactory.Payload(
                    channelId = channelId,
                    billIdentity = billIdentity,
                    businessDate = command.businessDate,
                    currency = currency,
                    businessTimezone = timezone,
                ),
            )

            require(bill.businessDate == command.businessDate) { "同一账单身份不能变更业务日期" }
            require(bill.currency == currency) { "同一账单身份不能变更币种" }
            require(bill.businessTimezone == timezone) { "同一账单身份不能变更业务时区" }
            val append = bill.appendRevision(command.revision)
            return response(bill, append)
        }

        private fun response(bill: AuthoritativeBill, append: BillRevisionAppendResult) = Response(
            billId = bill.id.toString(),
            channelId = bill.channelId,
            billIdentity = bill.billIdentity,
            currentRevision = bill.currentRevision,
            revision = append.revision.revision,
            revisionId = append.revision.id.toString(),
            idempotentReplay = append.idempotentReplay,
            becameCurrent = append.becameCurrent,
        )
    }

    data class Request(
        val channelId: String,
        val billIdentity: String,
        val businessDate: LocalDate,
        val currency: String,
        val businessTimezone: String,
        val revision: BillRevisionCreation,
    ) : Command<Response>

    data class Response(
        val billId: String,
        val channelId: String,
        val billIdentity: String,
        val currentRevision: String?,
        val revision: String,
        val revisionId: String,
        val idempotentReplay: Boolean,
        val becameCurrent: Boolean,
    )
}
