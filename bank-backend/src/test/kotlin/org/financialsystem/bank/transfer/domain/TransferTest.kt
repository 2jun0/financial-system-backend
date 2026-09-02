package org.financialsystem.bank.transfer.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TransferTest {
    @Test
    fun `송금을 개시하면 주어진 상태가 송금중으로 초기화된다`() {
        val transfer = initiateTransfer()

        assertThat(transfer.state).isEqualTo(TransferState.TRANSFERRING)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1, -100])
    fun `송금액이 0 이하이면 송금을 개시할 수 없다`(amount: Long) {
        assertThatThrownBy {
            initiateTransfer(amount)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `송금중인 송금을 성공 처리하면 성공 상태가 된다`() {
        val transfer = initiateTransfer()

        transfer.succeed()

        assertThat(transfer.state).isEqualTo(TransferState.SUCCEEDED)
    }

    @Test
    fun `송금중인 송금을 실패 처리하면 실패 상태가 된다`() {
        val transfer = initiateTransfer()

        transfer.fail()

        assertThat(transfer.state).isEqualTo(TransferState.FAILED)
    }

    @Test
    fun `성공한 송금은 실패 상태로 변경할 수 없다`() {
        val transfer = initiateTransfer()
        transfer.succeed()

        assertThatThrownBy {
            transfer.fail()
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `실패한 송금은 성공 상태로 변경할 수 없다`() {
        val transfer = initiateTransfer()
        transfer.fail()

        assertThatThrownBy {
            transfer.succeed()
        }.isInstanceOf(IllegalStateException::class.java)
    }

    private fun initiateTransfer(amount: Long = 100L) =
        Transfer.initiate(
            transferKey = "transfer-key",
            sourceAccountId = 1L,
            recipientBankCode = "BANK_B",
            recipientAccountNumber = "account-number",
            amount = amount,
        )
}
