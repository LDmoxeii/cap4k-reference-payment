package com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import java.time.Instant
import java.time.LocalDate

@DesignBlockMetadata(
    tag = "value_object",
    name = "ChannelStatement",
    packageName = "com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.values",
    description = "Immutable revisioned channel statement returned by PullChannelStatement",
    aggregates = ["ReconciliationBatch"],
    family = "value-object"
)
data class ChannelStatement(
    /**
     * 渠道标识
     */
    val channelId: String,
    /**
     * 币种
     */
    val currency: String,
    /**
     * 对账日期
     */
    val reconciliationDate: LocalDate,
    /**
     * 业务时区
     */
    val businessTimezone: String,
    /**
     * 对账单身份
     */
    val statementIdentity: String,
    /**
     * 对账单版本
     */
    val statementRevision: String,
    /**
     * 完整性
     */
    val completeness: StatementCompleteness,
    /**
     * 抓取时间
     */
    val fetchedAt: Instant,
    /**
     * 记录列表
     */
    val records: List<ChannelStatementRecord>
)
