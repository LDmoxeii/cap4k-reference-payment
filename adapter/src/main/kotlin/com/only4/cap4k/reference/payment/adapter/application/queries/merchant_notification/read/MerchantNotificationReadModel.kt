package com.only4.cap4k.reference.payment.adapter.application.queries.merchant_notification.read

import com.only4.cap4k.reference.payment.application.commands.merchant_notification.MerchantNotificationNotFoundException
import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.adapter.application.queries.ReferenceCursorToken
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.ListMerchantNotificationsEndpoint
import com.only4.cap4k.reference.payment.contract.endpoints.merchant_notification.api.MerchantNotificationView
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationDeliveryOutcome
import com.only4.cap4k.reference.payment.domain.aggregates.merchant_notification.enums.MerchantNotificationStatus
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/** SQL-owned notification detail and keyset collection; endpoint handlers never enumerate aggregates. */
@Service
class MerchantNotificationReadModel(
    private val entityManager: EntityManager,
) {
    fun detail(notificationId: String): MerchantNotificationView {
        val row = entityManager.createNativeQuery(
            "select id, notification_identity, content_identity, merchant_id, source_kind, " +
                "source_fact_identity, payment_id, content, status, finality, max_attempts, created_at " +
                "from merchant_notification where id = ?1",
        ).setParameter(1, notificationId.trim()).resultList.firstOrNull() as? Array<Any?>
            ?: throw MerchantNotificationNotFoundException(notificationId)
        val id = row[0].text("notificationId")
        val attempts = entityManager.createNativeQuery(
            "select id, attempt_sequence, delivery_identity, content_identity, outcome, diagnostic, recorded_at " +
                "from merchant_notification_delivery_attempt where merchant_notification_id = ?1 " +
                "order by attempt_sequence asc, id asc",
        ).setParameter(1, id).resultList.map { raw ->
            val attempt = raw as Array<Any?>
            MerchantNotificationView.DeliveryAttempt(
                deliveryAttemptId = attempt[0].text("deliveryAttemptId"),
                attemptSequence = (attempt[1] as Number).toInt(),
                deliveryIdentity = attempt[2].text("deliveryIdentity"),
                contentIdentity = attempt[3].text("contentIdentity"),
                outcome = outcome(attempt[4]).name,
                diagnostic = attempt[5]?.toString(),
                recordedAt = asInstant(attempt[6] ?: error("通知投递缺少 recordedAt")),
            )
        }
        return MerchantNotificationView(
            notificationId = id,
            notificationIdentity = row[1].text("notificationIdentity"),
            contentIdentity = row[2].text("contentIdentity"),
            merchantId = row[3].text("merchantId"),
            sourceKind = row[4].text("sourceKind"),
            sourceFactIdentity = row[5].text("sourceFactIdentity"),
            paymentId = row[6]?.toString(),
            content = row[7].text("content"),
            status = status(row[8]).name,
            finality = finality(row[9]),
            maxAttempts = (row[10] as Number).toInt(),
            createdAt = asInstant(row[11] ?: error("通知缺少 createdAt")),
            deliveryAttempts = attempts,
        )
    }

    fun list(request: ListMerchantNotificationsEndpoint.Request): ListMerchantNotificationsEndpoint.Response {
        val pageSize = (request.pageSize ?: DEFAULT_PAGE_SIZE).also {
            require(it in 1..MAX_PAGE_SIZE) { "pageSize 必须在 1 到 $MAX_PAGE_SIZE 之间" }
        }
        val filter = Filter.from(request)
        val cursor = request.cursor?.trim()?.takeIf(String::isNotEmpty)?.let {
            Cursor.decode(it, filter.fingerprint)
        }
        val clauses = mutableListOf<String>()
        val values = mutableListOf<Any>()
        fun where(sql: String, vararg parameters: Any) {
            clauses += sql
            values.addAll(parameters)
        }
        filter.merchantId?.let { where("n.merchant_id = ?", it) }
        filter.paymentId?.let { where("n.payment_id = ?", it) }
        filter.sourceKind?.let { where("n.source_kind = ?", it) }
        filter.status?.let { where("n.status = ?", it.value) }
        filter.createdFrom?.let { where("n.created_at >= ?", it) }
        filter.createdTo?.let { where("n.created_at <= ?", it) }
        cursor?.let { where("(n.created_at < ? or (n.created_at = ? and n.id < ?))", it.sortTime, it.sortTime, it.id) }
        val sql = buildString {
            append("select n.id, n.notification_identity, n.content_identity, n.merchant_id, n.source_kind, ")
            append("n.source_fact_identity, n.payment_id, n.status, n.finality, n.created_at, ")
            append("(select count(*) from merchant_notification_delivery_attempt a where a.merchant_notification_id = n.id) ")
            append("from merchant_notification n ")
            if (clauses.isNotEmpty()) append("where ").append(clauses.joinToString(" and ")).append(' ')
            append("order by n.created_at desc, n.id desc limit ?")
        }
        val query = entityManager.createNativeQuery(sql)
        values.forEachIndexed { index, value -> query.setParameter(index + 1, value) }
        query.setParameter(values.size + 1, pageSize + 1)
        val rows = query.resultList.map { it as Array<Any?> }
        val items = rows.take(pageSize).map { row ->
            ListMerchantNotificationsEndpoint.Response.Item(
                notificationId = row[0].text("notificationId"),
                notificationIdentity = row[1].text("notificationIdentity"),
                contentIdentity = row[2].text("contentIdentity"),
                merchantId = row[3].text("merchantId"),
                sourceKind = row[4].text("sourceKind"),
                sourceFactIdentity = row[5].text("sourceFactIdentity"),
                paymentId = row[6]?.toString(),
                status = status(row[7]).name,
                finality = finality(row[8]),
                sortTime = asInstant(row[9] ?: error("通知缺少 createdAt")),
                deliveryAttemptCount = (row[10] as Number).toInt(),
            )
        }
        return ListMerchantNotificationsEndpoint.Response(
            items = items,
            nextCursor = if (rows.size > pageSize) items.last().let {
                Cursor.encode(filter.fingerprint, it.sortTime, it.notificationId)
            } else null,
            pageSize = pageSize,
        )
    }

    private data class Filter(
        val merchantId: String?,
        val paymentId: String?,
        val sourceKind: String?,
        val status: MerchantNotificationStatus?,
        val createdFrom: Instant?,
        val createdTo: Instant?,
    ) {
        val fingerprint = hash(
            listOf(merchantId, paymentId, sourceKind, status?.name, createdFrom?.toString(), createdTo?.toString())
                .joinToString("|") { value -> "${value?.length ?: -1}:${value.orEmpty()}" },
        )

        companion object {
            fun from(request: ListMerchantNotificationsEndpoint.Request): Filter = Filter(
                merchantId = request.merchantId.normalized(),
                paymentId = request.paymentId.normalized(),
                sourceKind = request.sourceKind.normalized()?.uppercase()?.also {
                    require(it in setOf("PAYMENT", "REFUND", "RECONCILIATION", "SETTLEMENT")) {
                        "不支持的通知 sourceKind"
                    }
                },
                status = request.status.normalized()?.uppercase()?.let { value ->
                    MerchantNotificationStatus.entries.firstOrNull { it.name == value }
                        ?: throw IllegalArgumentException("不支持的通知 status")
                },
                createdFrom = request.createdFrom,
                createdTo = request.createdTo,
            ).also {
                require(it.createdFrom == null || it.createdTo == null || !it.createdFrom.isAfter(it.createdTo)) {
                    "createdFrom 不能晚于 createdTo"
                }
            }
        }
    }

    private data class Cursor(val sortTime: Instant, val id: String) {
        companion object {
            fun encode(fingerprint: String, sortTime: Instant, id: String): String =
                ReferenceCursorToken.encode("v1", fingerprint, sortTime.toString(), id)

            fun decode(raw: String, fingerprint: String): Cursor = try {
                val fields = ReferenceCursorToken.decode(raw, 4)
                if (fields[0] != "v1" || fields[1] != fingerprint || fields[3].isBlank()) {
                    throw InvalidCursorException()
                }
                Cursor(Instant.parse(fields[2]), fields[3])
            } catch (error: InvalidCursorException) {
                throw error
            } catch (_: Exception) {
                throw InvalidCursorException()
            }
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
        fun Any?.text(field: String): String = this?.toString()?.takeIf(String::isNotBlank)
            ?: error("通知缺少 $field")
        fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        fun status(raw: Any?): MerchantNotificationStatus =
            MerchantNotificationStatus.valueOfOrNull((raw as Number).toInt()) ?: error("通知状态投影无效")
        fun outcome(raw: Any?): MerchantNotificationDeliveryOutcome =
            MerchantNotificationDeliveryOutcome.valueOfOrNull((raw as Number).toInt()) ?: error("通知投递结果投影无效")
        fun finality(raw: Any?): Finality = enumValueOf(raw.text("finality"))
        fun asInstant(value: Any): Instant = when (value) {
            is Instant -> value
            is OffsetDateTime -> value.toInstant()
            is LocalDateTime -> value.toInstant(ZoneOffset.UTC)
            is Timestamp -> value.toInstant()
            is java.util.Date -> value.toInstant()
            else -> Instant.parse(value.toString())
        }
    }
}
