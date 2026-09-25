package com.only4.cap4k.reference.payment.application.manual_review

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.ManualReviewBlockingScope
import com.only4.cap4k.reference.payment.contract.common.ManualReviewEvidenceRef
import com.only4.cap4k.reference.payment.contract.common.ManualReviewReference
import com.only4.cap4k.reference.payment.domain._share.meta.manual_review_item.SManualReviewItem
import com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.ManualReviewItem
import com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.appendResolution
import com.only4.cap4k.reference.payment.domain.aggregates.manual_review_item.factory.ManualReviewItemFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

/**
 * One application-facing place for opening a persistent ManualReviewItem.  It deliberately stores
 * references to pre-existing PaymentReview/Disposition/Settlement facts; it never creates a parallel
 * decision or changes those facts. Factory persistence makes this part of the caller's CAP4K UoW.
 */
@Service
class ManualReviewSupport(
    private val clock: Clock,
) {
    fun open(opening: Opening): Opened {
        val normalized = opening.normalized()
        val existing = Mediator.repositories.findOne(
            SManualReviewItem.predicate { schema -> schema.reviewIdentity eq normalized.reviewIdentity },
        )
        if (existing != null) return Opened(existing, created = false)

        val item = Mediator.factories.create<ManualReviewItemFactory.Payload, ManualReviewItem>(
            ManualReviewItemFactory.Payload(
                reviewIdentity = normalized.reviewIdentity,
                type = normalized.type,
                status = normalized.status,
                finality = normalized.finality.name,
                merchantId = normalized.merchantId,
                paymentId = normalized.relatedRefs.firstOrNull { it.resourceType == "PAYMENT" }?.resourceId,
                originKind = normalized.originKind,
                originIdentity = normalized.originIdentity,
                summary = normalized.summary,
                relatedRefsJson = ManualReviewJson.refs(normalized.relatedRefs),
                blockingScopesJson = ManualReviewJson.scopes(normalized.blockingScopes),
                evidenceRefsJson = ManualReviewJson.evidence(normalized.evidenceRefs),
                sortTime = LocalDateTime.ofInstant(normalized.sortTime ?: Instant.now(clock), ZoneOffset.UTC),
            ),
        )
        return Opened(item, created = true)
    }

    /**
     * Appends the reference-facing resolution only after the caller has successfully applied the
     * originating aggregate's decision.  The item is a discoverability and audit resource: it never
     * becomes an alternate source of truth for a payment, reconciliation, settlement, or refund.
     */
    fun appendResolution(
        item: ManualReviewItem,
        resolutionIdentity: String,
        actorId: String,
        actorRole: String,
        outcome: String,
        reason: String,
        evidence: String,
        resolvedAt: Instant,
    ): Resolved {
        val normalizedIdentity = resolutionIdentity.required("resolutionIdentity")
        val appended = item.appendResolution(
            resolutionIdentity = normalizedIdentity,
            actorId = actorId.required("actorId"),
            actorRole = actorRole.required("actorRole").uppercase(),
            outcome = outcome.required("outcome").uppercase(),
            reason = reason.required("reason"),
            evidence = evidence.required("evidence"),
            resolvedAt = LocalDateTime.ofInstant(resolvedAt, ZoneOffset.UTC),
        )
        return Resolved(appended.created)
    }

    data class Opening(
        val reviewIdentity: String,
        val type: String,
        val merchantId: String? = null,
        val originKind: String,
        val originIdentity: String,
        val summary: String,
        val relatedRefs: List<ManualReviewReference>,
        val blockingScopes: List<ManualReviewBlockingScope>,
        val evidenceRefs: List<ManualReviewEvidenceRef>,
        val status: String = STATUS_OPEN,
        val finality: Finality = Finality.REVIEW_REQUIRED,
        val sortTime: Instant? = null,
    ) {
        internal fun normalized(): Opening = copy(
            reviewIdentity = reviewIdentity.required("reviewIdentity"),
            type = type.required("type").uppercase(),
            merchantId = merchantId.normalized(),
            originKind = originKind.required("originKind").uppercase(),
            originIdentity = originIdentity.required("originIdentity"),
            summary = summary.required("summary"),
            status = status.required("status").uppercase().also {
                require(it == STATUS_OPEN) { "ManualReview 仅允许由来源事实打开为 OPEN" }
            },
            relatedRefs = relatedRefs.normalizedRefs(),
            blockingScopes = blockingScopes.normalizedScopes(),
            evidenceRefs = evidenceRefs.normalizedEvidence(),
        )
    }

    data class Opened(val item: ManualReviewItem, val created: Boolean)
    data class Resolved(val created: Boolean)

    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_RESOLVED = "RESOLVED"

        private fun String.required(field: String): String = trim().also { require(it.isNotBlank()) { "$field 不能为空" } }
        private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

        private fun List<ManualReviewReference>.normalizedRefs(): List<ManualReviewReference> = map {
            ManualReviewReference(it.resourceType.required("relatedRefs.resourceType"), it.resourceId.required("relatedRefs.resourceId"))
        }.distinct().sortedWith(compareBy(ManualReviewReference::resourceType, ManualReviewReference::resourceId))

        private fun List<ManualReviewBlockingScope>.normalizedScopes(): List<ManualReviewBlockingScope> = map {
            ManualReviewBlockingScope(it.scopeType.required("blockingScopes.scopeType"), it.scopeId.required("blockingScopes.scopeId"))
        }.distinct().sortedWith(compareBy(ManualReviewBlockingScope::scopeType, ManualReviewBlockingScope::scopeId))

        private fun List<ManualReviewEvidenceRef>.normalizedEvidence(): List<ManualReviewEvidenceRef> = map {
            ManualReviewEvidenceRef(
                it.evidenceType.required("evidenceRefs.evidenceType"),
                it.evidenceId.required("evidenceRefs.evidenceId"),
                it.summary?.trim()?.takeIf(String::isNotEmpty),
            )
        }.distinct().sortedWith(compareBy(ManualReviewEvidenceRef::evidenceType, ManualReviewEvidenceRef::evidenceId, { it.summary.orEmpty() }))
    }
}

/** Minimal, deterministic JSON codec for the schema's canonical reference snapshots. */
object ManualReviewJson {
    fun refs(items: List<ManualReviewReference>): String = array(items) {
        "{\"resourceType\":${quote(it.resourceType)},\"resourceId\":${quote(it.resourceId)}}"
    }

    fun scopes(items: List<ManualReviewBlockingScope>): String = array(items) {
        "{\"scopeType\":${quote(it.scopeType)},\"scopeId\":${quote(it.scopeId)}}"
    }

    fun evidence(items: List<ManualReviewEvidenceRef>): String = array(items) {
        "{\"evidenceType\":${quote(it.evidenceType)},\"evidenceId\":${quote(it.evidenceId)},\"summary\":${it.summary?.let(::quote) ?: "null"}}"
    }

    fun decodeRefs(raw: String): List<ManualReviewReference> = objects(raw).map {
        ManualReviewReference(it.required("resourceType"), it.required("resourceId"))
    }

    fun decodeScopes(raw: String): List<ManualReviewBlockingScope> = objects(raw).map {
        ManualReviewBlockingScope(it.required("scopeType"), it.required("scopeId"))
    }

    fun decodeEvidence(raw: String): List<ManualReviewEvidenceRef> = objects(raw).map {
        ManualReviewEvidenceRef(it.required("evidenceType"), it.required("evidenceId"), it["summary"])
    }

    private fun <T> array(items: List<T>, render: (T) -> String): String = items.joinToString(prefix = "[", postfix = "]", transform = render)

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun objects(raw: String): List<Map<String, String?>> = Reader(raw).readArray()

    private fun Map<String, String?>.required(name: String): String = this[name]?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("ManualReview JSON 缺少 $name")

    private class Reader(private val text: String) {
        private var index = 0

        fun readArray(): List<Map<String, String?>> {
            space(); expect('[')
            val result = mutableListOf<Map<String, String?>>()
            space()
            if (take(']')) return result
            do {
                result += readObject()
                space()
            } while (take(','))
            expect(']'); space()
            require(index == text.length) { "ManualReview JSON 包含尾随内容" }
            return result
        }

        private fun readObject(): Map<String, String?> {
            space(); expect('{')
            val result = linkedMapOf<String, String?>()
            space()
            if (take('}')) return result
            do {
                val name = string()
                space(); expect(':'); space()
                result[name] = if (text.startsWith("null", index)) { index += 4; null } else string()
                space()
            } while (take(','))
            expect('}')
            return result
        }

        private fun string(): String {
            space(); expect('"')
            return buildString {
                while (index < text.length) {
                    val char = text[index++]
                    when (char) {
                        '"' -> return this.toString()
                        '\\' -> append(escape())
                        else -> append(char)
                    }
                }
                error("ManualReview JSON 字符串未结束")
            }
        }

        private fun escape(): Char = when (val char = next()) {
            '"', '\\', '/' -> char
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> text.substring(index, (index + 4).also { require(it <= text.length) { "非法 unicode escape" } })
                .also { index += 4 }.toInt(16).toChar()
            else -> error("非法 JSON escape: $char")
        }

        private fun next(): Char = text.getOrNull(index++) ?: error("ManualReview JSON 意外结束")
        private fun take(expected: Char): Boolean = if (text.getOrNull(index) == expected) { index++; true } else false
        private fun expect(expected: Char) { require(take(expected)) { "ManualReview JSON 预期 $expected" } }
        private fun space() { while (text.getOrNull(index)?.isWhitespace() == true) index++ }
    }
}
