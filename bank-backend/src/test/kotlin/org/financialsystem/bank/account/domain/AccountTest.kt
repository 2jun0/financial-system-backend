package org.financialsystem.bank.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AccountTest {
    @Test
    fun `출금 금액이 잔액보다 크면 출금할 수 없다`() {
        val balance = 10_000L
        val account = createAccount().also { it.credit(balance) }

        assertThatThrownBy { account.debit(10_001L) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(account.balance).isEqualTo(balance)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1, -100])
    fun `출금 금액이 0이거나 음수라면 출금할 수 없다`(debitAmount: Long) {
        val account = createAccount()

        assertThatThrownBy { account.debit(debitAmount) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(account.balance).isZero()
    }

    @ParameterizedTest
    @ValueSource(longs = [10_000L, 1_000L])
    fun `출금 금액만큼 잔액을 감소시킨다`(debitAmount: Long) {
        val balance = 10_000L
        val account = createAccount().also { it.credit(balance) }

        account.debit(debitAmount)

        assertThat(account.balance).isEqualTo(balance - debitAmount)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1, -100])
    fun `입금 금액이 0이거나 음수라면 입금할 수 없다`(creditAmount: Long) {
        val account = createAccount()

        assertThatThrownBy { account.credit(creditAmount) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(account.balance).isZero()
    }

    @ParameterizedTest
    @ValueSource(longs = [10_000L, 1_000L])
    fun `입금 금액만큼 잔액을 증가시킨다`(creditAmount: Long) {
        val balance = 10_000L
        val account = createAccount().also { it.credit(balance) }

        account.credit(creditAmount)

        assertThat(account.balance).isEqualTo(balance + creditAmount)
    }

    @Test
    fun `입금 후 잔액이 Long 범위를 초과하면 입금할 수 없다`() {
        val account = createAccount().also { it.credit(Long.MAX_VALUE) }

        assertThatThrownBy { account.credit(1L) }
            .isInstanceOf(ArithmeticException::class.java)

        assertThat(account.balance).isEqualTo(Long.MAX_VALUE)
    }

    private fun createAccount(): Account =
        Account.create(
            accountNumber = "123-123-123",
            customerId = 1L,
            name = "account",
        )
}
