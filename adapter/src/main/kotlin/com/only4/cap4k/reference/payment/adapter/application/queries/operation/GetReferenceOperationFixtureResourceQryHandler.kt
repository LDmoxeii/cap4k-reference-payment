package com.only4.cap4k.reference.payment.adapter.application.queries.operation

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.reference.payment.application.operations.OperationSupport
import com.only4.cap4k.reference.payment.application.queries.operation.GetReferenceOperationFixtureResourceQry
import org.springframework.stereotype.Service

@Service
@DesignBlockMetadata(
    tag = "query",
    name = "GetReferenceOperationFixtureResource",
    packageName = "reference_fixture.operation",
    description = "Read the authoritative resource exposed by a reference operation fixture",
    aggregates = ["Operation"],
    family = "query-handler",
)
class GetReferenceOperationFixtureResourceQryHandler(
    private val operations: OperationSupport,
) : QueryHandler<GetReferenceOperationFixtureResourceQry.Request, GetReferenceOperationFixtureResourceQry.Response> {
    override fun handle(query: GetReferenceOperationFixtureResourceQry.Request) =
        GetReferenceOperationFixtureResourceQry.Response(
            operations.getReadyResource("REFERENCE_FIXTURE", query.resourceId),
        )
}
