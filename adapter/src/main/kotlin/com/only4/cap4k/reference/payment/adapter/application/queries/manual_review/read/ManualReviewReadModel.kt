package com.only4.cap4k.reference.payment.adapter.application.queries.manual_review.read

import com.only4.cap4k.reference.payment.application.errors.InvalidCursorException
import com.only4.cap4k.reference.payment.application.errors.ManualReviewNotFoundException
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewJson
import com.only4.cap4k.reference.payment.application.reference.ReferencePolicyService
import com.only4.cap4k.reference.payment.adapter.application.queries.ReferenceCursorToken
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.ManualReviewItemView
import com.only4.cap4k.reference.payment.contract.common.ManualReviewResolutionView
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.ListManualReviewsEndpoint
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service

/**
 * Authoritative SQL read model. It is intentionally separate from endpoint code so pagination never
 * loads all ManualReviewItem aggregates or assembles a collection in a controller.
 */
@Service
class ManualReviewReadModel(
    private val entityManager: EntityManager,
    private val referencePolicy: ReferencePolicyService,
) {
    fun detail(reviewId: String): ManualReviewItemView {
        val row = entityManager.createNativeQuery(
            "select id, review_identity, type, status, finality, merchant_id, origin_kind, origin_identity, summary, " +
                "related_refs_json, blocking_scopes_json, evidence_refs_json, sort_time, created_at, resolved_at " +
                "from manual_review_item where id = ?1",
        ).setParameter(1, reviewId.trim()).resultList.firstOrNull() as? Array<Any?>
            ?: throw ManualReviewNotFoundException(reviewId)
        val itemId = row[0].toString()
        return ManualReviewItemView(
            reviewId = itemId,
            reviewIdentity = row[1].text("reviewIdentity"),
            type = row[2].text("type"),
            status = publicStatus(row[3].text("status")),
            finality = finality(row[4].text("finality")),
            merchantId = row[5]?.toString(),
            originKind = row[6].text("originKind"),
            originIdentity = row[7].text("originIdentity"),
            summary = row[8].text("summary"),
            relatedRefs = ManualReviewJson.decodeRefs(row[9].text("relatedRefsJson")),
            blockingScopes = ManualReviewJson.decodeScopes(row[10].text("blockingScopesJson")),
            evidenceRefs = ManualReviewJson.decodeEvidence(row[11].text("evidenceRefsJson")),
            sortTime = asInstant(row[12] ?: error("人工事项缺少 sortTime")),
            createdAt = asInstant(row[13] ?: error("人工事项缺少 createdAt")),
            resolvedAt = row[14]?.let(::asInstant),
            resolutions = resolutions(itemId),
        )
    }

    fun list(request: ListManualReviewsEndpoint.Request): ListManualReviewsEndpoint.Response {
        val pageSize = referencePolicy.pageSize(request.pageSize)
        val filter = Filter.from(request)
        val cursor = request.cursor?.trim()?.takeIf { it.isNotEmpty() }?.let { Cursor.decode(it, filter.fingerprint) }
        val clauses = mutableListOf<String>()
        val parameters = mutableListOf<Any>()
        fun where(clause: String, vararg values: Any) {
            clauses += clause
            parameters.addAll(values)
        }
        filter.merchantId?.let { where("i.merchant_id = ?", it) }
        filter.type?.let { where("i.type = ?", it) }
        filter.status?.let { where("i.status = ?", it) }
        filter.finality?.let { where("i.finality = ?", it.name) }
        filter.originKind?.let { where("i.origin_kind = ?", it) }
        filter.originIdentity?.let { where("i.origin_identity = ?", it) }
        filter.createdFrom?.let { where("i.sort_time >= ?", it) }
        filter.createdTo?.let { where("i.sort_time <= ?", it) }
        cursor?.let { where("(i.sort_time < ? or (i.sort_time = ? and i.id < ?))", it.sortTime, it.sortTime, it.id) }

        val sql = buildString {
            append("select i.id, i.review_identity, i.type, i.status, i.finality, i.merchant_id, i.origin_kind, ")
            append("i.origin_identity, i.summary, i.sort_time, i.resolved_at from manual_review_item i ")
            if (clauses.isNotEmpty()) append("where ").append(clauses.joinToString(" and ")).append(' ')
            append("order by i.sort_time desc, i.id desc limit ?")
        }
        val query = entityManager.createNativeQuery(sql)
        parameters.forEachIndexed { index, value -> query.setParameter(index + 1, value) }
        query.setParameter(parameters.size + 1, pageSize + 1)
        val rows = query.resultList.map { it as Array<Any?> }
        val items = rows.take(pageSize).map(::toListItem)
        return ListManualReviewsEndpoint.Response(
            items = items,
            nextCursor = if (rows.size > pageSize) {
                items.lastOrNull()?.let { Cursor.encode(filter.fingerprint, it.sortTime, it.reviewId) }
            } else {
                null
            },
            pageSize = pageSize,
        )
    }

    private fun resolutions(reviewId: String): List<ManualReviewResolutionView> = entityManager.createNativeQuery(
        "select id, resolution_identity, actor_id, actor_role, outcome, reason, evidence, resolved_at " +
            "from manual_review_resolution where manual_review_item_id = ?1 order by resolved_at asc, id asc",
    ).setParameter(1, reviewId).resultList.map { raw ->
        val row = raw as Array<Any?>
        ManualReviewResolutionView(
            resolutionId = row[0].toString(),
            resolutionIdentity = row[1].text("resolutionIdentity"),
            actorId = row[2].text("actorId"),
            actorRole = row[3].text("actorRole"),
            outcome = row[4].text("outcome"),
            reason = row[5].text("reason"),
            evidence = row[6].text("evidence"),
            resolvedAt = asInstant(row[7] ?: error("人工处置缺少 resolvedAt")),
        )
    }

    private fun toListItem(row: Array<Any?>): ListManualReviewsEndpoint.Response.Item =
        ListManualReviewsEndpoint.Response.Item(
            reviewId = row[0].toString(),
            reviewIdentity = row[1].text("reviewIdentity"),
            type = row[2].text("type"),
            status = publicStatus(row[3].text("status")),
            finality = finality(row[4].text("finality")),
            merchantId = row[5]?.toString(),
            originKind = row[6].text("originKind"),
            originIdentity = row[7].text("originIdentity"),
            summary = row[8].text("summary"),
            sortTime = asInstant(row[9] ?: error("人工事项缺少 sortTime")),
            resolvedAt = row[10]?.let(::asInstant),
        )

    private fun publicStatus(raw: String): String = when (raw) {
        "OPEN" -> "OPEN"
        "RESOLVED" -> "RESOLVED"
        else -> throw IllegalStateException("未知 ManualReview status: $raw")
    }

    private fun finality(raw: String): Finality = runCatching { Finality.valueOf(raw) }
        .getOrElse { throw IllegalStateException("未知 ManualReview finality: $raw") }

    private data class Filter(
        val merchantId: String?,
        val type: String?,
        val status: String?,
        val finality: Finality?,
        val originKind: String?,
        val originIdentity: String?,
        val createdFrom: Instant?,
        val createdTo: Instant?,
    ) {
        val fingerprint: String = sha256(
            listOf(merchantId, type, status, finality?.name, originKind, originIdentity, createdFrom?.toString(), createdTo?.toString())
                .joinToString("|") { it.orEmpty() },
        )

        companion object {
            fun from(request: ListManualReviewsEndpoint.Request): Filter = Filter(
                merchantId = request.merchantId.normalized(),
                type = request.type.normalized()?.uppercase(),
                status = request.status.normalized()?.uppercase()?.also {
                    require(it in setOf("OPEN", "RESOLVED")) { "不支持的 ManualReview status" }
                },
                finality = request.finality,
                originKind = request.originKind.normalized()?.uppercase(),
                originIdentity = request.originIdentity.normalized(),
                createdFrom = request.createdFrom,
                createdTo = request.createdTo,
            )
        }
    }

    private data class Cursor(val fingerprint: String, val sortTime: Instant, val id: String) {
        companion object {
            fun encode(fingerprint: String, sortTime: Instant, id: String): String =
                ReferenceCursorToken.encode("v1", fingerprint, sortTime.toString(), id)

            fun decode(raw: String, expectedFingerprint: String): Cursor = try {
                val fields = ReferenceCursorToken.decode(raw, 4)
                if (fields.size != 4 || fields[0] != "v1" || fields[1] != expectedFingerprint || fields[3].isBlank()) {
                    throw InvalidCursorException()
                }
                Cursor(fields[1], Instant.parse(fields[2]), fields[3])
            } catch (error: InvalidCursorException) {
                throw error
            } catch (_: Exception) {
                throw InvalidCursorException()
            }
        }
    }

    private companion object {
        fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        fun Any?.text(field: String): String = this?.toString()?.takeIf { it.isNotBlank() }
            ?: error("人工事项缺少 $field")
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
