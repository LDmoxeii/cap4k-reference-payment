package com.only4.cap4k.reference.payment.domain.aggregates.operation

import com.only4.cap4k.reference.payment.domain.aggregates.operation.factory.OperationFactory
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OperationBehaviorTest {
    @Test
    fun `accepted may process then converge to each of three terminal states`() {
        val succeeded = poll()
        succeeded.markProcessing(NOW.plusSeconds(1))
        assertThat(succeeded.status).isEqualTo("PROCESSING")
        assertThat(succeeded.finality).isEqualTo("NON_FINAL")
        succeeded.succeed(NOW.plusSeconds(2), "/api/resources/r1", """{"identity":"r1"}""")
        assertThat(succeeded.status).isEqualTo("SUCCEEDED")
        assertThat(succeeded.finality).isEqualTo("FINAL")
        assertThat(succeeded.resultJson).isEqualTo("""{"identity":"r1"}""")
        assertThat(succeeded.readAfterResourceUrl).isEqualTo("/api/resources/r1")

        val failed = poll()
        failed.markProcessing(NOW.plusSeconds(1))
        failed.fail(NOW.plusSeconds(2), "BUSINESS_CONFLICT", "异步失败", """{"reason":"known"}""")
        assertThat(failed.status).isEqualTo("FAILED")
        assertThat(failed.finality).isEqualTo("FINAL")
        assertThat(failed.errorCode).isEqualTo("BUSINESS_CONFLICT")
        assertThat(failed.errorDetailsJson).isEqualTo("""{"reason":"known"}""")

        val review = poll()
        review.markProcessing(NOW.plusSeconds(1))
        review.requireReview(NOW.plusSeconds(2), "review-1")
        assertThat(review.status).isEqualTo("REVIEW_REQUIRED")
        assertThat(review.finality).isEqualTo("REVIEW_REQUIRED")
        assertThat(review.reviewId).isEqualTo("review-1")

        listOf(succeeded, failed, review).forEach { terminal ->
            assertThat(terminal.completedAt).isEqualTo(NOW.plusSeconds(2))
            assertThatThrownBy { terminal.markProcessing(NOW.plusSeconds(3)) }
                .isInstanceOf(OperationStateTransitionException::class.java)
                .hasMessageContaining("INVALID_STATE_TRANSITION")
            assertThatThrownBy { terminal.succeed(NOW.plusSeconds(3), "/api/resources/r1") }
                .isInstanceOf(OperationStateTransitionException::class.java)
                .hasMessageContaining("INVALID_STATE_TRANSITION")
            assertThatThrownBy { terminal.fail(NOW.plusSeconds(3), "FAIL", "失败") }
                .isInstanceOf(OperationStateTransitionException::class.java)
                .hasMessageContaining("INVALID_STATE_TRANSITION")
            assertThatThrownBy { terminal.requireReview(NOW.plusSeconds(3), "review-2") }
                .isInstanceOf(OperationStateTransitionException::class.java)
                .hasMessageContaining("INVALID_STATE_TRANSITION")
        }
    }

    @Test
    fun `accepted can converge directly and invalid initial state is rejected`() {
        val direct = poll()
        direct.requireReview(NOW.plusSeconds(1), "review-direct")
        assertThat(direct.status).isEqualTo("REVIEW_REQUIRED")
        assertThatThrownBy {
            OperationFactory().create(payload().copy(status = "FAILED", finality = "FINAL"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INVALID_STATE_TRANSITION")
    }

    private fun poll(): Operation = OperationFactory().create(payload())

    private fun payload() = OperationFactory.Payload(
        merchantId = "M-001",
        commandType = "FixtureOperation",
        idempotencyKey = "key-1",
        canonicalRequestHash = "hash-1",
        resourceType = "FIXTURE",
        resourceId = "r1",
        status = "ACCEPTED",
        finality = "NON_FINAL",
        readAfterMode = "POLL",
        readAfterResourceUrl = "/api/resources/r1",
        acceptedAt = NOW,
        completedAt = null,
        retryAfterMs = 100,
    )

    companion object {
        private val NOW = LocalDateTime.parse("2026-09-23T00:00:00")
    }
}
