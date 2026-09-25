package com.only4.cap4k.reference.payment.domain.aggregates.payment_channel_result_receipt

import java.time.LocalDateTime

/** Records transport replay without replacing the immutable first-reception adjudication evidence. */
fun PaymentChannelResultReceipt.recordReplay(receivedAt: LocalDateTime) {
    require(!receivedAt.isBefore(recordedAt)) { "result receipt replay cannot precede its first reception" }
    receiveCount += 1
    lastReceivedAt = receivedAt
}

fun PaymentChannelResultReceipt.onCreate() {
    require(resultIdentity.isNotBlank()) { "resultIdentity 不能为空" }
    require(payloadIdentity.isNotBlank()) { "payloadIdentity 不能为空" }
    require(channelId.isNotBlank()) { "channelId 不能为空" }
    require(rawEvidence.isNotBlank()) { "rawEvidence 不能为空" }
    require(amount.signum() > 0) { "结果金额必须为正数" }
    require(currency.isNotBlank()) { "currency 不能为空" }
    require(receiveCount == 1) { "新建结果收件的 receiveCount 必须为 1" }
    require(!lastReceivedAt.isBefore(recordedAt)) { "lastReceivedAt 不能早于 recordedAt" }
}

fun PaymentChannelResultReceipt.onDeleted() = Unit
