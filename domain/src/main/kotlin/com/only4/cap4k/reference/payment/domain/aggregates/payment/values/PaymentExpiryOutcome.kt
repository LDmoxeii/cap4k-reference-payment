package com.only4.cap4k.reference.payment.domain.aggregates.payment.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus

@DesignBlockMetadata(
    tag = "value_object",
    name = "PaymentExpiryOutcome",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.payment.values",
    description = "Transient outcome returned after idempotently adjudicating one payment expiry",
    aggregates = ["Payment"],
    family = "value-object"
)
data class PaymentExpiryOutcome(
    val paymentStatus: PaymentStatus,
    val closedNow: Boolean,
    val reviewOpenedNow: Boolean,
    val reviewIdentity: String?
)
