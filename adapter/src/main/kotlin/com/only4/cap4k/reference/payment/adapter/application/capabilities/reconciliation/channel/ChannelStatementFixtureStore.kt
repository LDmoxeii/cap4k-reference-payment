package com.only4.cap4k.reference.payment.adapter.application.capabilities.reconciliation.channel

import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values.ChannelStatement
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Reference-only deterministic channel statement provider state.
 *
 * Production integrations replace this adapter with a real channel statement provider. Tests and
 * local demonstrations publish immutable statement revisions explicitly; absence is a provider
 * failure rather than an implicit empty statement.
 */
@Component
class ChannelStatementFixtureStore {
    private val statements = ConcurrentHashMap<Scope, List<ChannelStatement>>()

    fun publish(statement: ChannelStatement) {
        val scope = Scope(statement.channelId, statement.currency.uppercase(), statement.reconciliationDate)
        statements.compute(scope) { _, current ->
            val retained = current.orEmpty().filterNot {
                it.statementIdentity == statement.statementIdentity &&
                    it.statementRevision == statement.statementRevision
            }
            retained + statement.copy(currency = statement.currency.uppercase())
        }
    }

    fun latest(channelId: String, currency: String, reconciliationDate: LocalDate): ChannelStatement =
        statements[Scope(channelId, currency.uppercase(), reconciliationDate)]?.lastOrNull()
            ?: throw ChannelStatementUnavailableException(channelId, currency, reconciliationDate)

    fun clear() = statements.clear()

    private data class Scope(
        val channelId: String,
        val currency: String,
        val reconciliationDate: LocalDate,
    )
}

class ChannelStatementUnavailableException(
    channelId: String,
    currency: String,
    reconciliationDate: LocalDate,
) : RuntimeException("channel statement is unavailable for $channelId/$currency/$reconciliationDate")
