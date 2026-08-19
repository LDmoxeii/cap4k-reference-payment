package com.only4.cap4k.reference.payment.domain.aggregates.payment

/**
 * Signals that a refund reservation lost the current payment budget race.
 *
 * The adapter maps this domain conflict to the stable HTTP concurrency code.
 */
class RefundBudgetConflictException(message: String) : RuntimeException(message)
