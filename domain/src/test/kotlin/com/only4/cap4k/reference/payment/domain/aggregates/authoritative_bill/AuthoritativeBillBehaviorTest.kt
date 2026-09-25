package com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill

import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.reference.payment.domain.aggregates.authoritative_bill.factory.AuthoritativeBillFactory
import com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AuthoritativeBillBehaviorTest {
    @Test
    @DisplayName("PAY-AC-087/094 — revision 重放使用不可变指纹，迟到 revision 不回退 current")
    fun `revision replay is fingerprint guarded and current revision remains monotonic`() {
        val identifiers = TestIdentifiers()
        MediatorSupport.configure(identifiers)
        try {
            val bill = AuthoritativeBillFactory().create(
                AuthoritativeBillFactory.Payload(
                    channelId = "C-BILL-DOMAIN",
                    billIdentity = "bill-domain-1",
                    businessDate = LocalDate.parse("2031-01-01"),
                    currency = "CNY",
                    businessTimezone = "Asia/Shanghai",
                ),
            )

            val revisionTwo = bill.appendRevision(revision("2", "fingerprint-2"))
            val replay = bill.appendRevision(revision("2", "fingerprint-2"))
            assertThat(revisionTwo.idempotentReplay).isFalse()
            assertThat(replay.idempotentReplay).isTrue()
            assertThat(replay.revision).isSameAs(revisionTwo.revision)

            assertThatThrownBy { bill.appendRevision(revision("2", "different-fingerprint")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("正文指纹冲突")

            val lateOne = bill.appendRevision(revision("1", "fingerprint-1"))
            assertThat(lateOne.becameCurrent).isFalse()
            assertThat(bill.currentRevision).isEqualTo("2")
            assertThat(bill.billRevisions.map { it.revision }).containsExactlyInAnyOrder("1", "2")
        } finally {
            MediatorSupport.release(identifiers)
        }
    }

    private fun revision(value: String, fingerprint: String) = BillRevisionCreation(
        revision = value,
        completeness = StatementCompleteness.COMPLETE,
        rawEvidence = "evidence://bill-domain-1/$value",
        payloadFingerprint = fingerprint,
        publishedAt = Instant.parse("2031-01-01T00:00:00Z"),
        records = emptyList(),
    )

    private class TestIdentifiers : IdentifierGenerator {
        private val sequence = AtomicInteger()

        override fun <T : Any> next(strategy: String, type: KClass<T>): T {
            require(strategy == "uuid7")
            val suffix = sequence.incrementAndGet().toString().padStart(12, '0')
            @Suppress("UNCHECKED_CAST")
            return "018f22a0-0000-7000-8000-$suffix" as T
        }
    }
}
