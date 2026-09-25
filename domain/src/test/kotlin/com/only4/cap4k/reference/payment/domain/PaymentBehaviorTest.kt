package com.only4.cap4k.reference.payment.domain

import com.only4.cap4k.reference.payment.domain.aggregates.payment.Payment
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentAttemptId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import com.only4.cap4k.reference.payment.domain.aggregates.payment.SettlementFeeRule
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentNotificationIntentState
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentReviewType
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.currentReviewEligibility
import com.only4.cap4k.reference.payment.domain.aggregates.payment.expire
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordChannelResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.createAttempt
import com.only4.cap4k.reference.payment.domain.aggregates.payment.freezeAttemptSubmission
import com.only4.cap4k.reference.payment.domain.aggregates.payment.recordAttemptSubmission
import com.only4.cap4k.reference.payment.domain.aggregates.payment.startAttempt
import com.only4.cap4k.reference.payment.domain.values.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PaymentBehaviorTest {
    @Test
    @DisplayName("PAY-AC-016/100 — attempt 创建、提交、渠道受理与资金成功严格分离")
    fun `payment attempt progresses through created submitted and accepted without forming success`() {
        val payment = givenPendingPayment()
        val attempt = payment.createAttempt(
            channelId = "C-001",
            channelConfigurationId = "018f22a0-0000-7000-8000-000000000001",
            channelConfigurationSnapshot = "channelId=C-001;currency=CNY",
            requestIdentity = "request-explicit-001",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
            interactionInformation = "reference-channel=C-001",
        )
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000012")

        assertThat(attempt.status).isEqualTo(PaymentAttemptStatus.CREATED)
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(attempt.paymentSubmissionReceipts).isEmpty()
        assertThat(payment.successFactFormed).isFalse()

        payment.freezeAttemptSubmission(attempt.id, LocalDateTime.parse("2026-08-17T08:00:01"))
        assertThat(attempt.status).isEqualTo(PaymentAttemptStatus.SUBMITTED)
        assertThat(attempt.submissionIdentity).isEqualTo("payment-submit:${attempt.id}")
        assertThat(payment.status).isEqualTo(PaymentStatus.PROCESSING)

        payment.recordAttemptSubmission(
            paymentAttemptId = attempt.id,
            submissionIdentity = requireNotNull(attempt.submissionIdentity),
            submittedAt = LocalDateTime.parse("2026-08-17T08:00:01"),
            outcome = "ACCEPTED",
            channelReference = "reference-${attempt.requestIdentity}",
            diagnosticSummary = "支付渠道已受理请求",
        )
        assertThat(attempt.status).isEqualTo(PaymentAttemptStatus.ACCEPTED)
        assertThat(attempt.acceptedAt).isEqualTo(LocalDateTime.parse("2026-08-17T08:00:01"))
        assertThat(attempt.paymentSubmissionReceipts).hasSize(1)
        assertThat(payment.status).isEqualTo(PaymentStatus.PROCESSING)
        assertThat(payment.successFactFormed).isFalse()
        assertThat(payment.merchantSuccessNotificationIntentCount).isZero()

        assertThatIllegalStateException().isThrownBy {
            payment.recordAttemptSubmission(
                attempt.id,
                requireNotNull(attempt.submissionIdentity),
                LocalDateTime.parse("2026-08-17T08:00:02"),
                "ACCEPTED",
                "duplicate",
                "支付渠道已受理请求",
            )
        }
        assertThat(attempt.paymentSubmissionReceipts).hasSize(1)
    }

    @Test
    @DisplayName("PAY-AC-010/100 — 未收敛 attempt 下创建第二尝试必须给出风险原因并阻断结算")
    fun `concurrent attempt requires an explicit risk reason`() {
        val payment = givenPendingPayment()
        val first = payment.createAttempt(
            "C-001",
            "018f22a0-0000-7000-8000-000000000001",
            "channelId=C-001;currency=CNY",
            "request-risk-001",
            LocalDateTime.parse("2026-08-17T08:00:00"),
            "reference-channel=C-001",
        )
        first.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000013")

        assertThatIllegalStateException().isThrownBy {
            payment.createAttempt(
                "C-001",
                "018f22a0-0000-7000-8000-000000000001",
                "channelId=C-001;currency=CNY",
                "request-risk-002",
                LocalDateTime.parse("2026-08-17T08:00:01"),
                "reference-channel=C-001",
            )
        }.withMessage("PAYMENT_ATTEMPT_RISK_REASON_REQUIRED")

        val second = payment.createAttempt(
            "C-001",
            "018f22a0-0000-7000-8000-000000000001",
            "channelId=C-001;currency=CNY",
            "request-risk-002",
            LocalDateTime.parse("2026-08-17T08:00:01"),
            "reference-channel=C-001",
            riskReason = "商户确认前次跳转已失效，需要重新发起",
        )
        second.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000014")
        assertThat(second.status).isEqualTo(PaymentAttemptStatus.CREATED)
        assertThat(second.riskReason).isNotBlank()
        assertThat(payment.reviewCases).anyMatch { it.type == PaymentReviewType.CONCURRENT_ATTEMPT_RISK }
        assertThat(payment.currentReviewEligibility().settlementEligible).isFalse()
    }

    @Test
    @DisplayName("PAY-AC-012 — 金额、精度与币种输入边界")
    fun `money keeps exact cents and rejects invalid payment amounts`() {
        val money = Money.of(BigDecimal("100"), "cny")

        assertThat(money.amount).isEqualByComparingTo("100.00")
        assertThat(money.currency).isEqualTo("CNY")
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal.ZERO, "CNY") }
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal("10.001"), "CNY") }
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal.ONE, "CN") }
        assertThatIllegalArgumentException().isThrownBy { Money.of(BigDecimal.ONE, "USD") }
    }

    @Test
    @DisplayName("PAY-AC-004/005/015 — 首次成功、重复通知与成功后失败不回退")
    fun `accepted channel result forms success once and later failure cannot roll it back`() {
        // Given：Payment 尚未成功，且先建立一个处理中尝试；费用规则作为成功时快照输入。
        val payment = givenPendingPayment()
        val attempt = payment.startAttempt(
            channelId = "C-001",
            channelConfigurationId = "018f22a0-0000-7000-8000-000000000001",
            channelConfigurationSnapshot = "channelId=C-001;currency=CNY",
            requestIdentity = "request-001",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
        )
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000011")

        // When：首次可信 SUCCESS 形成成功事实；随后用同身份重放，再发送不同身份的 FAILED。
        val success = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-001",
            channelTransactionId = "CT-001",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "SUCCESS",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:01:01"),
            verified = true,
            verificationSummary = "verified",
            settlementFeeRule = givenSettlementFeeRule(),
        )
        val duplicate = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-001",
            channelTransactionId = "CT-001",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "SUCCESS",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:02:00"),
            verified = true,
            verificationSummary = "verified",
        )
        val conflict = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-002",
            channelTransactionId = "CT-002",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "FAILED",
            occurredAt = LocalDateTime.parse("2026-08-17T08:03:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:03:01"),
            verified = true,
            verificationSummary = "verified",
        )

        // Then：按“支付状态 / 尝试状态 / 成功事实 / 通知意图 / 结算资格 / 持久化回执”分组读取结果。
        assertThat(success.accepted).isTrue()
        assertThat(success.successFactFormedNow).isTrue()
        assertThat(duplicate.duplicate).isTrue()
        assertThat(duplicate.successFactFormedNow).isFalse()
        assertThat(conflict.accepted).isFalse()
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCEEDED)
        assertThat(payment.successFactFormed).isTrue()
        assertThat(payment.settlementFeeFactIdentity).isEqualTo("payment:${payment.id}:settlement-fee")
        assertThat(payment.settlementFeeRate).isEqualByComparingTo("0.006")
        assertThat(payment.settlementFeeBasisPoints).isEqualTo(60)
        assertThat(payment.settlementFixedFeeAmount).isEqualByComparingTo("0")
        assertThat(payment.settlementFeeRoundingMode).isEqualTo("HALF_UP")
        assertThat(payment.settlementFeeCurrencyPrecision).isEqualTo(2)
        assertThat(payment.settlementFeeCalculationAmount).isEqualByComparingTo("100.00")
        assertThat(payment.settlementFeeAmount).isEqualByComparingTo("0.60")
        assertThat(payment.settlementFeeFormedAt).isEqualTo(LocalDateTime.parse("2026-08-17T08:01:00"))
        assertThat(payment.merchantSuccessNotificationIntentCount).isEqualTo(1)
        assertThat(attempt.paymentNotificationReceipts).hasSize(2)
        assertThat(attempt.paymentNotificationReceipts.first().receiveCount).isEqualTo(2)
        assertThat(payment.settlementBlocked).isTrue()
        assertThatIllegalStateException().isThrownBy {
            payment.startAttempt(
                channelId = "C-001",
                channelConfigurationId = "018f22a0-0000-7000-8000-000000000001",
                channelConfigurationSnapshot = "channelId=C-001",
                requestIdentity = "request-002",
                initiatedAt = LocalDateTime.parse("2026-08-17T08:04:00"),
            )
        }
    }

    @Test
    fun `failed attempt returns payment to payable while the retry window remains`() {
        val payment = givenPendingPayment()
        val first = payment.startAttempt(
            channelId = "C-001",
            channelConfigurationId = "cfg-1",
            channelConfigurationSnapshot = "snapshot-1",
            requestIdentity = "request-failure-1",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
        ).also {
            it.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000071")
        }

        val failed = payment.recordChannelResult(
            paymentAttemptId = first.id,
            channelId = "C-001",
            notificationId = "N-FAILURE-RETRY",
            channelTransactionId = "CT-FAILURE-RETRY",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "FAILED",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:01:01"),
            verified = true,
            verificationSummary = "verified",
        )
        val retry = payment.createAttempt(
            channelId = "C-001",
            channelConfigurationId = "cfg-2",
            channelConfigurationSnapshot = "snapshot-2",
            requestIdentity = "request-failure-2",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:02:00"),
            interactionInformation = "redirect://retry",
        )

        assertThat(failed.disposition).isEqualTo(ChannelResultDisposition.FAILURE_ACCEPTED)
        assertThat(first.status).isEqualTo(PaymentAttemptStatus.FAILED)
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(retry.status).isEqualTo(PaymentAttemptStatus.CREATED)
        assertThat(payment.attempts).hasSize(2)
    }

    @Test
    fun `failed attempt makes payment failed after the retry window`() {
        val payment = givenPendingPayment(LocalDateTime.parse("2026-08-17T08:01:00"))
        val attempt = payment.startAttempt(
            channelId = "C-001",
            channelConfigurationId = "cfg",
            channelConfigurationSnapshot = "snapshot",
            requestIdentity = "request-expired-failure",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
        ).also {
            it.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000072")
        }

        payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-001",
            notificationId = "N-EXPIRED-FAILURE",
            channelTransactionId = "CT-EXPIRED-FAILURE",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "FAILED",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:30"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:02:00"),
            verified = true,
            verificationSummary = "verified",
        )

        assertThat(attempt.status).isEqualTo(PaymentAttemptStatus.FAILED)
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
    }

    @Test
    @DisplayName("PAY-AC-014 — 首次成功冻结 effective feeRate、舍入和币种精度")
    fun `first success freezes the effective policy fee snapshot and later result does not recalculate it`() {
        val payment = givenPendingPayment()
        val attempt = payment.startAttempt(
            channelId = "C-001", channelConfigurationId = "cfg", channelConfigurationSnapshot = "snapshot",
            requestIdentity = "policy-snapshot", initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
        )
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000026")

        payment.recordChannelResult(
            attempt.id, "C-001", "N-POLICY-1", "CT-POLICY-1", BigDecimal("100.00"), "CNY", "SUCCESS",
            LocalDateTime.parse("2026-08-17T08:01:00"), LocalDateTime.parse("2026-08-17T08:01:01"),
            true, "verified",
            SettlementFeeRule(
                configurationId = "reference-policy", basisPoints = 80, feeRate = BigDecimal("0.008"),
                fixedFeeAmount = BigDecimal.ZERO, roundingMode = RoundingMode.HALF_UP, currencyPrecision = 2,
            ),
        )
        // A subsequent (conflicting) callback carries a different live policy input.  It must not
        // recalculate the already-persisted first-success fact.
        payment.recordChannelResult(
            attempt.id, "C-001", "N-POLICY-2", "CT-POLICY-2", BigDecimal("100.00"), "CNY", "FAILED",
            LocalDateTime.parse("2026-08-17T08:02:00"), LocalDateTime.parse("2026-08-17T08:02:01"),
            true, "verified",
            SettlementFeeRule(
                configurationId = "reference-policy", basisPoints = 60, feeRate = BigDecimal("0.006"),
                fixedFeeAmount = BigDecimal.ZERO, roundingMode = RoundingMode.DOWN, currencyPrecision = 0,
            ),
        )

        assertThat(payment.settlementFeeRate).isEqualByComparingTo("0.008")
        assertThat(payment.settlementFeeRoundingMode).isEqualTo("HALF_UP")
        assertThat(payment.settlementFeeCurrencyPrecision).isEqualTo(2)
        assertThat(payment.settlementFeeAmount).isEqualByComparingTo("0.80")
    }


    @Test
    @DisplayName("PAY-AC-006 — 未验证或渠道不匹配通知保留拒绝证据")
    fun `verified channel mismatch is rejected and retained as a notification receipt`() {
        val payment = givenPendingPayment()
        val attempt = payment.startAttempt(
            channelId = "C-001",
            channelConfigurationId = "018f22a0-0000-7000-8000-000000000001",
            channelConfigurationSnapshot = "channelId=C-001;currency=CNY",
            requestIdentity = "request-mismatch",
            initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
        )
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000012")

        val decision = payment.recordChannelResult(
            paymentAttemptId = attempt.id,
            channelId = "C-999",
            notificationId = "N-CHANNEL-MISMATCH",
            channelTransactionId = "CT-CHANNEL-MISMATCH",
            amount = BigDecimal("100.00"),
            currency = "CNY",
            result = "SUCCESS",
            occurredAt = LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt = LocalDateTime.parse("2026-08-17T08:01:01"),
            verified = true,
            verificationSummary = "signature verified",
        )

        assertThat(decision.accepted).isFalse()
        assertThat(decision.rejectionSummary).contains("与支付尝试渠道")
        assertThat(payment.status).isEqualTo(PaymentStatus.PROCESSING)
        assertThat(attempt.paymentNotificationReceipts).hasSize(1)
        assertThat(attempt.paymentNotificationReceipts.single().decision).isEqualTo(ChannelResultDisposition.REJECTED)
        assertThat(attempt.paymentNotificationReceipts.single().rejectionSummary).contains("与支付尝试渠道")
    }


    @Test
    @DisplayName("PAY-AC-007 — 到期关闭与重复扫描幂等")
    fun `expired payment without pending attempt closes idempotently and cannot start`() {
        val payment = givenPendingPayment(LocalDateTime.parse("2026-08-17T08:00:00"))
        val first = payment.expire(LocalDateTime.parse("2026-08-17T08:00:01"))
        val replay = payment.expire(LocalDateTime.parse("2026-08-17T08:01:00"))

        assertThat(first.closedNow).isTrue()
        assertThat(replay.closedNow).isFalse()
        assertThat(payment.status).isEqualTo(PaymentStatus.CLOSED)
        assertThat(payment.closeReason).isEqualTo("PAYMENT_EXPIRED_WITHOUT_PENDING_ATTEMPT")
        assertThatIllegalStateException().isThrownBy {
            payment.startAttempt("C-001", "cfg", "snapshot", "late", LocalDateTime.parse("2026-08-17T08:02:00"))
        }.withMessageContaining("PAYMENT_EXPIRED")
    }

    @Test
    @DisplayName("PAY-AC-008 — 到期未知结果与稳定复核")
    fun `expired payment with processing attempt becomes result unknown with one stable review`() {
        val payment = givenPendingPayment(LocalDateTime.parse("2026-08-17T08:00:00"))
        val attempt = payment.startAttempt("C-001", "cfg", "snapshot", "request", LocalDateTime.parse("2026-08-17T07:59:00"))
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000021")

        val first = payment.expire(LocalDateTime.parse("2026-08-17T08:00:01"))
        val replay = payment.expire(LocalDateTime.parse("2026-08-17T08:01:00"))

        assertThat(first.reviewOpenedNow).isTrue()
        assertThat(replay.reviewOpenedNow).isFalse()
        assertThat(payment.status).isEqualTo(PaymentStatus.RESULT_UNKNOWN)
        assertThat(attempt.status).isEqualTo(PaymentAttemptStatus.RESULT_UNKNOWN)
        assertThat(payment.reviewCases).hasSize(1)
        assertThat(payment.reviewCases.single().type).isEqualTo(PaymentReviewType.EXPIRY_RESULT_UNKNOWN)
        assertThat(payment.currentReviewEligibility().settlementEligible).isFalse()
    }

    @Test
    @DisplayName("PAY-AC-008/100/101 — 可信 UNKNOWN 先保持非终态并按 policy 延后复核")
    fun `trusted unknown stays non final until the configured review threshold`() {
        val payment = givenPendingPayment()
        val attempt = payment.startAttempt(
            "C-001",
            "cfg",
            "snapshot",
            "request-unknown-policy",
            LocalDateTime.parse("2026-08-17T08:00:00"),
        ).also { it.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000024") }
        val receivedAt = LocalDateTime.parse("2026-08-17T08:01:01")

        val accepted = payment.recordChannelResult(
            attempt.id,
            "C-001",
            "N-UNKNOWN-POLICY",
            "CT-UNKNOWN-POLICY",
            BigDecimal("100.00"),
            "CNY",
            "UNKNOWN",
            LocalDateTime.parse("2026-08-17T08:01:00"),
            receivedAt,
            true,
            "verified",
        )
        val beforeThreshold = payment.expire(receivedAt.plusMinutes(4), Duration.ofMinutes(5))
        val atThreshold = payment.expire(receivedAt.plusMinutes(5), Duration.ofMinutes(5))

        assertThat(accepted.disposition).isEqualTo(ChannelResultDisposition.UNKNOWN_ACCEPTED)
        assertThat(payment.status).isEqualTo(PaymentStatus.RESULT_UNKNOWN)
        assertThat(attempt.status).isEqualTo(PaymentAttemptStatus.RESULT_UNKNOWN)
        assertThat(beforeThreshold.reviewOpenedNow).isFalse()
        assertThat(atThreshold.reviewOpenedNow).isTrue()
        assertThat(payment.reviewCases).hasSize(1)
        assertThat(payment.currentReviewEligibility().settlementEligible).isFalse()
    }

    @Test
    @DisplayName("PAY-AC-009 — 终态后的迟到成功进入复核")
    fun `late success after closed terminal preserves terminal state and opens held review`() {
        val payment = givenPendingPayment().also { it.status = PaymentStatus.CLOSED }
        val attempt = PaymentAttempt(
            channelId = "C-001", channelConfigurationId = "cfg", channelConfigurationSnapshot = "snapshot",
            requestIdentity = "request", status = PaymentAttemptStatus.FAILED,
            initiatedAt = LocalDateTime.parse("2026-08-17T07:59:00"), finalResult = PaymentAttemptFinalResult.FAILED,
        ).also {
            it.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000022")
            payment.attempts.add(it)
        }

        val outcome = payment.recordChannelResult(
            attempt.id, "C-001", "N-LATE", "CT-LATE", BigDecimal("100.00"), "CNY", "SUCCESS",
            LocalDateTime.parse("2026-08-17T08:01:00"), LocalDateTime.parse("2026-08-17T08:02:00"),
            true, "verified",
        )

        assertThat(outcome.conflicting).isTrue()
        assertThat(payment.status).isEqualTo(PaymentStatus.CLOSED)
        assertThat(payment.successFactFormed).isFalse()
        assertThat(payment.reviewCases.single().type).isEqualTo(PaymentReviewType.LATE_SUCCESS_AFTER_TERMINAL)
        assertThat(payment.merchantSuccessNotificationIntentState).isEqualTo(PaymentNotificationIntentState.HELD_FOR_REVIEW)
        assertThat(attempt.paymentNotificationReceipts.single().accepted).isTrue()
    }

    @Test
    @DisplayName("PAY-AC-010 — 多尝试成功但成功事实/费用/通知意图只形成一次")
    fun `two trustworthy attempt successes form revenue fee and notification intent only once`() {
        val payment = givenPendingPayment().also { it.status = PaymentStatus.PROCESSING }
        val first = givenProcessingAttempt("018f22a0-0000-7000-8000-000000000023", "request-1")
        val second = givenProcessingAttempt("018f22a0-0000-7000-8000-000000000024", "request-2")
        payment.attempts.add(first)
        payment.attempts.add(second)

        payment.recordChannelResult(
            first.id, "C-001", "N-FIRST", "CT-FIRST", BigDecimal("100.00"), "CNY", "SUCCESS",
            LocalDateTime.parse("2026-08-17T08:01:00"), LocalDateTime.parse("2026-08-17T08:01:01"),
            true, "verified", givenSettlementFeeRule(),
        )
        val conflict = payment.recordChannelResult(
            second.id, "C-001", "N-SECOND", "CT-SECOND", BigDecimal("100.00"), "CNY", "SUCCESS",
            LocalDateTime.parse("2026-08-17T08:02:00"), LocalDateTime.parse("2026-08-17T08:02:01"),
            true, "verified", givenSettlementFeeRule(),
        )

        assertThat(conflict.conflicting).isTrue()
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCEEDED)
        assertThat(payment.channelTransactionId).isEqualTo("CT-FIRST")
        assertThat(payment.settlementFeeAmount).isEqualByComparingTo("0.60")
        assertThat(payment.merchantSuccessNotificationIntentCount).isEqualTo(1)
        assertThat(payment.reviewCases.single().type).isEqualTo(PaymentReviewType.MULTIPLE_ATTEMPT_SUCCESS)
        assertThat(payment.currentReviewEligibility().settlementEligible).isFalse()
    }

    @Test
    @DisplayName("PAY-AC-005/015 — 同通知身份载荷冲突追加证据")
    fun `notification identity reuse with another payload appends evidence without overwriting first receipt`() {
        val payment = givenPendingPayment()
        val attempt = payment.startAttempt("C-001", "cfg", "snapshot", "request", LocalDateTime.parse("2026-08-17T08:00:00"))
        attempt.id = PaymentAttemptId.parse("018f22a0-0000-7000-8000-000000000025")
        payment.recordChannelResult(
            attempt.id, "C-001", "N-REUSED", "CT-1", BigDecimal("100.00"), "CNY", "SUCCESS",
            LocalDateTime.parse("2026-08-17T08:01:00"), LocalDateTime.parse("2026-08-17T08:01:01"),
            true, "verified", givenSettlementFeeRule(),
        )
        val originalPayload = attempt.paymentNotificationReceipts.single().payloadIdentity
        payment.recordChannelResult(
            attempt.id, "C-001", "N-REUSED", "CT-2", BigDecimal("100.00"), "CNY", "FAILED",
            LocalDateTime.parse("2026-08-17T08:02:00"), LocalDateTime.parse("2026-08-17T08:02:01"),
            true, "verified",
        )

        assertThat(attempt.paymentNotificationReceipts).hasSize(2)
        assertThat(attempt.paymentNotificationReceipts.first().payloadIdentity).isEqualTo(originalPayload)
        assertThat(attempt.paymentNotificationReceipts.first().decision).isEqualTo(ChannelResultDisposition.SUCCESS_ACCEPTED)
        assertThat(attempt.paymentNotificationReceipts.last().decision).isEqualTo(ChannelResultDisposition.CONFLICT)
        assertThat(payment.reviewCases).anyMatch { it.type == PaymentReviewType.NOTIFICATION_PAYLOAD_CONFLICT }
    }

    private fun givenProcessingAttempt(id: String, request: String) = PaymentAttempt(
        channelId = "C-001", channelConfigurationId = "cfg", channelConfigurationSnapshot = "snapshot",
        requestIdentity = request, status = PaymentAttemptStatus.ACCEPTED,
        initiatedAt = LocalDateTime.parse("2026-08-17T08:00:00"),
    ).also { it.id = PaymentAttemptId.parse(id) }

    private fun givenSettlementFeeRule(): SettlementFeeRule = SettlementFeeRule(
        configurationId = "018f22a0-0000-7000-8000-000000000001",
        basisPoints = 60,
        fixedFeeAmount = BigDecimal.ZERO,
        roundingMode = RoundingMode.HALF_UP,
        currencyPrecision = 2,
        feeRate = BigDecimal("0.006"),
    )

    private fun givenPendingPayment(expiresAt: LocalDateTime = LocalDateTime.parse("2030-01-01T00:00:00")): Payment = Payment(
        merchantId = "M-001",
        merchantOrderNumber = "O-001",
        idempotencyKey = "K-001",
        amount = BigDecimal("100.00"),
        currency = "CNY",
        paymentMethod = "CARD",
        status = PaymentStatus.PENDING,
        expiresAt = expiresAt,
        reservedRefundAmount = BigDecimal.ZERO,
        successfulRefundAmount = BigDecimal.ZERO,
    ).also {
        it.id = PaymentId.parse("018f22a0-0000-7000-8000-000000000010")
    }
}
