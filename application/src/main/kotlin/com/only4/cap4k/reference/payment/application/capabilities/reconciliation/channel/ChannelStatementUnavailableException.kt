package com.only4.cap4k.reference.payment.application.capabilities.reconciliation.channel

import java.time.LocalDate

/**
 * A deliberately narrow provider condition: the requested authoritative statement cannot be
 * read *yet*.  Reconciliation signal handling may retain a diagnostic and retry this condition;
 * it must not be used to hide validation, revision, or business conflicts.
 */
class ChannelStatementUnavailableException(
    channelId: String,
    currency: String,
    reconciliationDate: LocalDate,
) : RuntimeException("channel statement is unavailable for $channelId/$currency/$reconciliationDate")
