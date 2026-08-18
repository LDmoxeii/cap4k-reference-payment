package com.only4.cap4k.reference.payment.domain

import com.only4.cap4k.reference.payment.domain.aggregates.merchant_channel_configuration.enums.MerchantChannelConfigurationStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.ChannelResultDisposition
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptFinalResult
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentAttemptStatus
import com.only4.cap4k.reference.payment.domain.aggregates.payment.enums.PaymentStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedEnumConverterTest {
    @Test
    fun `checked in enum converters preserve stable numeric values`() {
        assertRoundTrip(PaymentStatus.entries, PaymentStatus.Converter()::convertToDatabaseColumn, PaymentStatus.Converter()::convertToEntityAttribute)
        assertRoundTrip(PaymentAttemptStatus.entries, PaymentAttemptStatus.Converter()::convertToDatabaseColumn, PaymentAttemptStatus.Converter()::convertToEntityAttribute)
        assertRoundTrip(PaymentAttemptFinalResult.entries, PaymentAttemptFinalResult.Converter()::convertToDatabaseColumn, PaymentAttemptFinalResult.Converter()::convertToEntityAttribute)
        assertRoundTrip(ChannelResultDisposition.entries, ChannelResultDisposition.Converter()::convertToDatabaseColumn, ChannelResultDisposition.Converter()::convertToEntityAttribute)
        assertRoundTrip(
            MerchantChannelConfigurationStatus.entries,
            MerchantChannelConfigurationStatus.Converter()::convertToDatabaseColumn,
            MerchantChannelConfigurationStatus.Converter()::convertToEntityAttribute,
        )
    }

    @Test
    fun `channel result disposition exposes typed fields and authored domain behavior`() {
        assertThat(ChannelResultDisposition.RECEIVED.group).isEqualTo("pending")
        assertThat(ChannelResultDisposition.RECEIVED.terminal).isFalse()
        assertThat(ChannelResultDisposition.RECEIVED.isTerminal()).isFalse()

        assertThat(ChannelResultDisposition.SUCCESS_ACCEPTED.isAccepted()).isTrue()
        assertThat(ChannelResultDisposition.ACCEPTED_DUPLICATE.isAccepted()).isTrue()
        assertThat(ChannelResultDisposition.ACCEPTED_DUPLICATE.isDuplicate()).isTrue()

        assertThat(ChannelResultDisposition.REJECTED.isRejected()).isTrue()
        assertThat(ChannelResultDisposition.REJECTED_DUPLICATE.isDuplicate()).isTrue()
        assertThat(ChannelResultDisposition.CONFLICT.isConflicting()).isTrue()

        assertThat(ChannelResultDisposition.entries.filterNot { it == ChannelResultDisposition.RECEIVED })
            .allMatch { it.isTerminal() }
    }

    private fun <E> assertRoundTrip(
        entries: Iterable<E>,
        toDatabase: (E?) -> Int?,
        toEntity: (Int?) -> E?,
    ) where E : Enum<E> {
        entries.forEach { entry ->
            val stored = requireNotNull(toDatabase(entry))
            assertThat(toEntity(stored)).isEqualTo(entry)
        }
        assertThat(toDatabase(null)).isNull()
        assertThat(toEntity(null)).isNull()
        assertThat(toEntity(Int.MIN_VALUE)).isNull()
    }
}
