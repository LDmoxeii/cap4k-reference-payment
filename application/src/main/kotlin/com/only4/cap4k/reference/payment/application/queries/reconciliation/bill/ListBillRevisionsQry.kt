package com.only4.cap4k.reference.payment.application.queries.reconciliation.bill

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBillId

@DesignBlockMetadata(
    tag = "query",
    name = "ListBillRevisions",
    packageName = "reconciliation.bill",
    description = "List every persisted authoritative bill revision in ascending revision order",
    aggregates = ["AuthoritativeBill"],
    family = "query",
)
object ListBillRevisionsQry {
    data class Request(val billId: AuthoritativeBillId) : Query<Response>
    data class Response(val bill: GetAuthoritativeBillQry.Response)
}
