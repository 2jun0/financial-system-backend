package org.financialsystem.bank.receipt.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.money.domain.PositiveAmount
import org.junit.jupiter.api.Test

class ReceiptTest {
    private val transferKey = "transfer-key"
    private val recipientAccountId = 1L
    private val sourceBankCode = "BANK_A"
    private val sourceAccountNumber = "source-account-number"
    private val amount = PositiveAmount(10_000L)

    @Test
    fun `수취를 시작하면 수취중 상태로 생성한다`() {
        val receipt = createReceipt()

        assertThat(receipt.transferKey).isEqualTo(transferKey)
        assertThat(receipt.recipientAccountId).isEqualTo(recipientAccountId)
        assertThat(receipt.sourceBankCode).isEqualTo(sourceBankCode)
        assertThat(receipt.sourceAccountNumber).isEqualTo(sourceAccountNumber)
        assertThat(receipt.amount).isEqualTo(amount.value)
        assertThat(receipt.state).isEqualTo(ReceiptState.RECEIVING)
    }

    @Test
    fun `수취중인 거래를 성공 처리한다`() {
        val receipt = createReceipt()

        receipt.succeed()

        assertThat(receipt.state).isEqualTo(ReceiptState.SUCCEEDED)
    }

    @Test
    fun `수취중인 거래를 실패 처리한다`() {
        val receipt = createReceipt()

        receipt.fail()

        assertThat(receipt.state).isEqualTo(ReceiptState.FAILED)
    }

    @Test
    fun `최종 상태인 수취는 다시 성공 또는 실패 처리할 수 없다`() {
        val succeededReceipt = createReceipt().also { it.succeed() }
        val failedReceipt = createReceipt().also { it.fail() }

        assertThatThrownBy { succeededReceipt.fail() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { failedReceipt.succeed() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun createReceipt(): Receipt =
        Receipt.initiate(
            transferKey = transferKey,
            recipientAccountId = recipientAccountId,
            sourceBankCode = sourceBankCode,
            sourceAccountNumber = sourceAccountNumber,
            amount = amount,
        )
}
