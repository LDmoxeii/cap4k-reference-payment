package com.only4.cap4k.reference.payment.domain.aggregates.payment

/**
 * Signals that a refund reservation would exceed the current payment budget.
 *
 * This covers both a directly excessive request and the loser of a concurrent reservation race;
 * the adapter maps both to the contract's stable REFUND_BUDGET_EXCEEDED code.
 */
class RefundBudgetConflictException(message: String) : RuntimeException(message)
