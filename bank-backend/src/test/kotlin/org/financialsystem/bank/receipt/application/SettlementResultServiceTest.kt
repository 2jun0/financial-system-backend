package org.financialsystem.bank.receipt.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.domain.Receipt
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.settlement.application.SettlementResultService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class SettlementResultServiceTest {
    @Mock
    lateinit var receiptRepository: ReceiptRepository

    @Mock
    lateinit var accountRepository: AccountRepository

    @Mock
    lateinit var accountTransactionRepository: AccountTransactionRepository

    private val transferKey = "transfer-key"
    private val recipientAccountId = 1L
    private val recipientAccountNumber = "BANK_B_ACCOUNT"
    private val sourceBankCode = "BANK_A"
    private val sourceAccountNumber = "BANK_A_ACCOUNT"
    private val amount = PositiveAmount(10_000L)
    private lateinit var settlementResultService: SettlementResultService

    @BeforeEach
    fun setUp() {
        settlementResultService =
            SettlementResultService(receiptRepository, accountRepository, accountTransactionRepository)
    }

    @Test
    fun `결제완료 결과를 반영하면 수취 계좌에 입금하고 입금 기록과 수취 성공을 남긴다`() {
        val receipt = createReceipt()
        val account = createAccount()
        given(receiptRepository.findByTransferKey(transferKey)).willReturn(receipt)
        given(accountRepository.findById(recipientAccountId)).willReturn(Optional.of(account))

        settlementResultService.applySettledResult(transferKey)

        val transactionCaptor = ArgumentCaptor.forClass(AccountTransaction::class.java)
        verify(accountTransactionRepository).save(transactionCaptor.capture())
        val transaction = transactionCaptor.value
        assertThat(receipt.state).isEqualTo(ReceiptState.SUCCEEDED)
        assertThat(account.balance).isEqualTo(amount.value)
        assertThat(transaction.accountId).isEqualTo(recipientAccountId)
        assertThat(transaction.transferKey).isEqualTo(transferKey)
        assertThat(transaction.otherBankCode).isEqualTo(sourceBankCode)
        assertThat(transaction.otherAccountNumber).isEqualTo(sourceAccountNumber)
        assertThat(transaction.amount).isEqualTo(amount.value)
        assertThat(transaction.type).isEqualTo(AccountTransactionType.CREDIT)
    }

    @Test
    fun `이미 성공한 수취에 같은 결과를 다시 반영하면 입금을 반복하지 않는다`() {
        val receipt = createReceipt().also { it.succeed() }
        given(receiptRepository.findByTransferKey(transferKey)).willReturn(receipt)

        settlementResultService.applySettledResult(transferKey)

        assertThat(receipt.state).isEqualTo(ReceiptState.SUCCEEDED)
    }

    @Test
    fun `실패한 수취에 결제완료 결과를 반영하면 상태를 변경하지 않는다`() {
        val receipt = createReceipt().also { it.fail() }
        given(receiptRepository.findByTransferKey(transferKey)).willReturn(receipt)

        assertThatThrownBy { settlementResultService.applySettledResult(transferKey) }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(receipt.state).isEqualTo(ReceiptState.FAILED)
    }

    @Test
    fun `결제거절 결과를 반영하면 입금 없이 수취를 실패 처리한다`() {
        val receipt = createReceipt()
        given(receiptRepository.findByTransferKey(transferKey)).willReturn(receipt)

        settlementResultService.applyRejectedResult(transferKey)

        assertThat(receipt.state).isEqualTo(ReceiptState.FAILED)
    }

    @Test
    fun `이미 실패한 수취에 같은 결과를 다시 반영하면 상태를 유지한다`() {
        val receipt = createReceipt().also { it.fail() }
        given(receiptRepository.findByTransferKey(transferKey)).willReturn(receipt)

        settlementResultService.applyRejectedResult(transferKey)

        assertThat(receipt.state).isEqualTo(ReceiptState.FAILED)
    }

    @Test
    fun `수취 기록이 없는 결제거절 결과는 새 기록을 만들지 않는다`() {
        given(receiptRepository.findByTransferKey(transferKey)).willReturn(null)

        settlementResultService.applyRejectedResult(transferKey)

        verify(receiptRepository).findByTransferKey(transferKey)
        verifyNoMoreInteractions(receiptRepository)
    }

    @Test
    fun `성공한 수취에 결제거절 결과를 반영하면 상태를 변경하지 않는다`() {
        val receipt = createReceipt().also { it.succeed() }
        given(receiptRepository.findByTransferKey(transferKey)).willReturn(receipt)

        assertThatThrownBy { settlementResultService.applyRejectedResult(transferKey) }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(receipt.state).isEqualTo(ReceiptState.SUCCEEDED)
    }

    private fun createReceipt(): Receipt =
        Receipt.initiate(
            transferKey = transferKey,
            recipientAccountId = recipientAccountId,
            sourceBankCode = sourceBankCode,
            sourceAccountNumber = sourceAccountNumber,
            amount = amount,
        )

    private fun createAccount(): Account =
        Account.create(recipientAccountNumber, customerId = 1L, name = "수취 계좌").also {
            ReflectionTestUtils.setField(it, "id", recipientAccountId)
        }
}
