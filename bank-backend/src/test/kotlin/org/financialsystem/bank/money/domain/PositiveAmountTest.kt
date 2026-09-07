package org.financialsystem.bank.money.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PositiveAmountTest {
    @Test
    fun `양수로 금액을 생성한다`() {
        val amount = PositiveAmount(1L)

        assertThat(amount.value).isEqualTo(1L)
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, -1L, -100L])
    fun `0이거나 음수인 금액은 생성할 수 없다`(value: Long) {
        assertThatThrownBy { PositiveAmount(value) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
