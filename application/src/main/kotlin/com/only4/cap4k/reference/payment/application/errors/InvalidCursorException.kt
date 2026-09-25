package com.only4.cap4k.reference.payment.application.errors

/** A cursor is bound to its original filters and can never be re-used across another query. */
class InvalidCursorException : PaymentApplicationException(
    code = "INVALID_CURSOR",
    message = "分页游标无效或与当前筛选条件不匹配",
    details = mapOf(
        "field" to "cursor",
        "reason" to "FILTER_MISMATCH_OR_INVALID",
    ),
)
