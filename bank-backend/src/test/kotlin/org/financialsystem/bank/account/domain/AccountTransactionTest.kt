package org.financialsystem.bank.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.money.domain.PositiveAmount
import org.junit.jupiter.api.Test

class AccountTransactionTest {
    @Test
    fun `출금 기록을 저장한다`() {
        val transaction =
            AccountTransaction.recordDebit(
                accountId = 1L,
                transferKey = "transfer-key",
                recipientBankCode = "BANK_B",
                recipientAccountNumber = "recipient-account-number",
                amount = PositiveAmount(10_000L),
            )

        assertThat(transaction.accountId).isEqualTo(1L)
        assertThat(transaction.transferKey).isEqualTo("transfer-key")
        assertThat(transaction.otherBankCode).isEqualTo("BANK_B")
        assertThat(transaction.otherAccountNumber).isEqualTo("recipient-account-number")
        assertThat(transaction.amount).isEqualTo(-10_000L)
        assertThat(transaction.type).isEqualTo(AccountTransactionType.DEBIT)
    }

    @Test
    fun `입금 기록을 저장한다`() {
        val transaction =
            AccountTransaction.recordCredit(
                accountId = 2L,
                transferKey = "transfer-key",
                sourceBankCode = "BANK_A",
                sourceAccountNumber = "source-account-number",
                amount = PositiveAmount(10_000L),
            )

        assertThat(transaction.accountId).isEqualTo(2L)
        assertThat(transaction.transferKey).isEqualTo("transfer-key")
        assertThat(transaction.otherBankCode).isEqualTo("BANK_A")
        assertThat(transaction.otherAccountNumber).isEqualTo("source-account-number")
        assertThat(transaction.amount).isEqualTo(10_000L)
        assertThat(transaction.type).isEqualTo(AccountTransactionType.CREDIT)
    }

    @Test
    fun `보상 기록을 저장한다`() {
        val transaction =
            AccountTransaction.recordCompensation(
                accountId = 1L,
                transferKey = "transfer-key",
                recipientBankCode = "BANK_B",
                recipientAccountNumber = "recipient-account-number",
                amount = PositiveAmount(10_000L),
            )

        assertThat(transaction.accountId).isEqualTo(1L)
        assertThat(transaction.transferKey).isEqualTo("transfer-key")
        assertThat(transaction.otherBankCode).isEqualTo("BANK_B")
        assertThat(transaction.otherAccountNumber).isEqualTo("recipient-account-number")
        assertThat(transaction.amount).isEqualTo(10_000L)
        assertThat(transaction.type).isEqualTo(AccountTransactionType.COMPENSATION)
    }
}
