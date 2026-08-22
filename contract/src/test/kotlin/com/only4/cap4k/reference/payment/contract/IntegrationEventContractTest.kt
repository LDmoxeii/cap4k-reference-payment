package com.only4.cap4k.reference.payment.contract

import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.reference.payment.contract.events.integration.inbound.reconciliation.ChannelStatementAvailableIntegrationEvent
import com.only4.cap4k.reference.payment.contract.events.integration.outbound.merchant_settlement.MerchantSettlementCompletedIntegrationEvent
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntegrationEventContractTest {

    @Test
    fun `published integration event names are stable and match their runtime annotations`() {
        assertEventName(
            ChannelStatementAvailableIntegrationEvent::class.java,
            ChannelStatementAvailableIntegrationEvent.EVENT_NAME,
            "payment.reconciliation.channel-statement-available.v1",
        )
        assertEventName(
            MerchantSettlementCompletedIntegrationEvent::class.java,
            MerchantSettlementCompletedIntegrationEvent.EVENT_NAME,
            "payment.merchant-settlement.completed.v1",
        )
    }

    @Test
    fun `published payloads remain transport neutral immutable facts`() {
        val statement = ChannelStatementAvailableIntegrationEvent(
            eventIdentity = "statement-available-1",
            channelId = "C-001",
            currency = "CNY",
            reconciliationDate = LocalDate.parse("2026-08-21"),
            statementIdentity = "statement-2026-08-21",
            statementRevision = "2",
            publishedAt = Instant.parse("2026-08-22T00:05:00Z"),
        )
        assertEquals("2", statement.statementRevision)
        assertNull(statement.correlationIdentity)
        assertNull(statement.causationIdentity)

        val completed = MerchantSettlementCompletedIntegrationEvent(
            eventIdentity = "settlement-completed-1",
            settlementId = "settlement-1",
            merchantId = "M-001",
            channelId = "C-001",
            currency = "CNY",
            netAmount = BigDecimal("127.00"),
            completedAt = Instant.parse("2026-08-22T00:15:00Z"),
        )
        assertEquals(BigDecimal("127.00"), completed.netAmount)
        assertNull(completed.correlationIdentity)
        assertNull(completed.causationIdentity)
    }

    private fun assertEventName(type: Class<*>, declared: String, expected: String) {
        assertEquals(expected, declared)
        assertEquals(expected, type.getAnnotation(IntegrationEvent::class.java).value)
    }
}