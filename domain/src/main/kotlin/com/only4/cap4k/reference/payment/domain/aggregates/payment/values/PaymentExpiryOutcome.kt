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
    /**
     * 支付状态
     */
    val paymentStatus: PaymentStatus,
    /**
     * 当前是否关闭
     */
    val closedNow: Boolean,
    /**
     * 当前是否打开复核
     */
    val reviewOpenedNow: Boolean,
    /**
     * 复核身份
     */
    val reviewIdentity: String?
)
