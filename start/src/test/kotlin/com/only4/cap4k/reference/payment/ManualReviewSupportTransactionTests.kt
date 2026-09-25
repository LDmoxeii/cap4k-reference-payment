package com.only4.cap4k.reference.payment

import com.only4.cap4k.reference.payment.adapter.application.queries.manual_review.read.ManualReviewReadModel
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.reference.payment.application.manual_review.ManualReviewSupport
import com.only4.cap4k.reference.payment.contract.common.Finality
import com.only4.cap4k.reference.payment.contract.common.ManualReviewBlockingScope
import com.only4.cap4k.reference.payment.contract.common.ManualReviewEvidenceRef
import com.only4.cap4k.reference.payment.contract.common.ManualReviewReference
import com.only4.cap4k.reference.payment.contract.endpoints.manual_review.api.ListManualReviewsEndpoint
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies the UoW boundary used by all payment/refund/reconciliation/settlement hooks.  The source
 * commands supply their already-recorded facts; this test keeps the persistence guarantee focused
 * on the common ManualReview bridge instead of duplicating four lifecycle fixtures.
 */
@SpringBootTest
class ManualReviewSupportTransactionTests(
    @param:Autowired private val readModel: ManualReviewReadModel,
    @param:Autowired private val jdbcTemplate: JdbcTemplate,
) {
    @Test
    fun `stable source identity replays once and search detail preserve structured source references`() {
        val marker = "manual-review-${UUID.randomUUID()}"
        val payment = opening(marker, "PAYMENT_REVIEW", "payment-review:$marker", "PAYMENT_EXPIRY_RESULT_UNKNOWN")

        Mediator.commands.send(ManualReviewSupportTestCommand.Request(payment))
        Mediator.commands.send(ManualReviewSupportTestCommand.Request(payment))

        val sources = listOf(
            opening(marker, "REFUND_CALLBACK", "refund:$marker:attempt-1:notification-1", "REFUND_RESULT_UNKNOWN"),
            opening(marker, "RECONCILIATION_ITEM", "batch:$marker:run-1:difference-1", "RECONCILIATION_AMOUNT_MISMATCH"),
            opening(marker, "SETTLEMENT_CALLBACK", "settlement:$marker:attempt-1:notification-1", "SETTLEMENT_RESULT_CONFLICT"),
        )
        sources.forEach { opening -> Mediator.commands.send(ManualReviewSupportTestCommand.Request(opening)) }

        assertThat(count(marker)).isEqualTo(4)

        val firstPage = readModel.list(ListManualReviewsEndpoint.Request(merchantId = marker, pageSize = 2))
        assertThat(firstPage.items).hasSize(2)
        assertThat(firstPage.nextCursor).isNotBlank()
        val secondPage = readModel.list(
            ListManualReviewsEndpoint.Request(merchantId = marker, pageSize = 2, cursor = firstPage.nextCursor),
        )
        assertThat(secondPage.items).hasSize(2)
        assertThat(secondPage.items.map { it.reviewId }).doesNotContainAnyElementsOf(firstPage.items.map { it.reviewId })

        val detail = readModel.detail(firstPage.items.first().reviewId)
        assertThat(detail.status).isEqualTo("OPEN")
        assertThat(detail.finality).isEqualTo(Finality.REVIEW_REQUIRED)
        assertThat(detail.relatedRefs).allSatisfy {
            assertThat(it.resourceType).isNotBlank()
            assertThat(it.resourceId).isNotBlank()
        }
        assertThat(detail.blockingScopes).allSatisfy {
            assertThat(it.scopeType).isNotBlank()
            assertThat(it.scopeId).isNotBlank()
        }
        assertThat(detail.evidenceRefs).allSatisfy {
            assertThat(it.evidenceType).isNotBlank()
            assertThat(it.evidenceId).isNotBlank()
        }
    }

    @Test
    fun `manual review is rolled back with its owning business unit of work`() {
        val marker = "manual-review-rollback-${UUID.randomUUID()}"
        val opening = opening(marker, "PAYMENT_REVIEW", "payment-review:$marker", "PAYMENT_MULTIPLE_ATTEMPT_SUCCESS")

        assertThatThrownBy {
            Mediator.commands.send(ManualReviewSupportTestCommand.Request(opening, rollbackAfterOpen = true))
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(count(marker)).isZero()
    }

    private fun opening(marker: String, originKind: String, originIdentity: String, type: String) =
        ManualReviewSupport.Opening(
            reviewIdentity = "manual-review-test:$originKind:$originIdentity",
            type = type,
            merchantId = marker,
            originKind = originKind,
            originIdentity = originIdentity,
            summary = "来源 $originKind 的可复核业务事实",
            relatedRefs = listOf(
                ManualReviewReference("SOURCE", originIdentity),
                ManualReviewReference("TRACE", marker),
            ),
            blockingScopes = listOf(ManualReviewBlockingScope("MERCHANT", marker)),
            evidenceRefs = listOf(ManualReviewEvidenceRef("SOURCE_EVIDENCE", originIdentity, "immutable source fact")),
            finality = Finality.REVIEW_REQUIRED,
            sortTime = Instant.parse("2026-09-22T00:00:00Z"),
        )

    private fun count(marker: String): Int = jdbcTemplate.queryForObject(
        "select count(*) from manual_review_item where merchant_id = ?",
        Int::class.java,
        marker,
    ) ?: 0
}
