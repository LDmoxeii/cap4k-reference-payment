package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch

import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind
import com.only4.cap4k.reference.payment.domain.aggregates.refund.RefundId
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReconciliationConfirmationFactCreation(
    val sourceDifferenceIdentity: String,
    val merchantId: String,
    val channelId: String,
    val operatorIdentity: String,
    val confirmationReason: String,
    val evidence: String,
    val transactionKind: ReconciliationTransactionKind,
    val amount: BigDecimal,
    val currency: String,
    val externalTransactionIdentity: String,
    val paymentId: PaymentId?,
    val refundId: RefundId?,
    val confirmedAt: LocalDateTime
)
