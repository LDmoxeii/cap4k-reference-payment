package com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.factory

import com.only4.cap4k.analysis.metadata.AggregateElementMetadata
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.AuthoritativeBill
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
@AggregateElementMetadata(
    aggregate = "AuthoritativeBill",
    name = "AuthoritativeBillFactory",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.factory",
    description = "",
    type = "factory",
    root = false,
)
class AuthoritativeBillFactory : AggregateFactory<AuthoritativeBillFactory.Payload, AuthoritativeBill> {
    override fun create(entityPayload: Payload): AuthoritativeBill = AuthoritativeBill(
        channelId = entityPayload.channelId,
        billIdentity = entityPayload.billIdentity,
        businessDate = entityPayload.businessDate,
        currency = entityPayload.currency,
        businessTimezone = entityPayload.businessTimezone,
        currentRevision = null,
        currentRevisionId = null,
        lastFetchDiagnostic = null,
        readAttemptCount = 0,
        lastReadAttemptAt = null,
    )

    data class Payload(
        val channelId: String,
        val billIdentity: String,
        val businessDate: LocalDate,
        val currency: String,
        val businessTimezone: String,
    ) : AggregatePayload<AuthoritativeBill>
}
