package com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill

import java.time.Instant

data class BillAvailableSignalCreation(
    val signalIdentity: String,
    val announcedRevision: String,
    val publishedAt: Instant,
    val receivedAt: Instant,
)
