package com.only4.cap4k.reference.payment.application.queries.operation

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.reference.payment.contract.common.OperationView

@DesignBlockMetadata(
    tag = "query",
    name = "GetReferenceOperationFixtureResource",
    packageName = "reference_fixture.operation",
    description = "Read the authoritative resource exposed by a reference operation fixture",
    aggregates = ["Operation"],
    family = "query",
)
object GetReferenceOperationFixtureResourceQry {
    data class Request(val resourceId: String) : Query<Response>
    data class Response(val operation: OperationView)
}
