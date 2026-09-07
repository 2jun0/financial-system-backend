package org.financialsystem.bank.transfer.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.account.application.exception.AccountNotFoundException
import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.domain.exception.InsufficientBalanceException
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class TransferServiceTest {
    @Mock
    lateinit var transferRepository: TransferRepository

    @Mock
    lateinit var accountTransactionRepository: AccountTransactionRepository

    @Mock
    lateinit var accountRepository: AccountRepository

    private val fakeTransferKeyGenerator = FakeTransferKeyGenerator()
    private val bankCode = "BANK_A"
    private val customerId = 1L
    private val sourceAccountId = 10L
    private val recipientBankCode = "BANK_B"
    private val recipientAccountNumber = "recipient-account-number"
    private val amount = PositiveAmount(10_000L)
    private val transferRequest =
        TransferRequest(
            recipientBankCode = recipientBankCode,
            recipientAccountNumber = recipientAccountNumber,
            amount = amount,
        )
    private lateinit var transferService: TransferService

    @BeforeEach
    fun setUp() {
        transferService =
            TransferService(
                transferRepository,
                fakeTransferKeyGenerator,
                accountRepository,
                accountTransactionRepository,
                bankCode,
            )
    }

    @Test
    fun `송금을 개시하면 잔액을 차감하고 같은 키로 출금 내역과 송금중인 거래를 저장한다`() {
        val account = Account.create("source-account-number", customerId, "출금 계좌")
        account.credit(PositiveAmount(30_000L))
        given(accountRepository.findById(sourceAccountId)).willReturn(Optional.of(account))
        given(transferRepository.save(any(Transfer::class.java)))
            .willAnswer { it.getArgument<Transfer>(0) }

        val transfer =
            transferService.initiateTransfer(
                customerId,
                sourceAccountId,
                transferRequest,
            )

        val transactionCaptor = ArgumentCaptor.forClass(AccountTransaction::class.java)
        verify(accountTransactionRepository).save(transactionCaptor.capture())
        val transaction = transactionCaptor.value
        val transferCaptor = ArgumentCaptor.forClass(Transfer::class.java)
        verify(transferRepository).save(transferCaptor.capture())

        assertThat(account.balance).isEqualTo(20_000L)
        assertThat(transfer).isSameAs(transferCaptor.value)
        assertThat(transfer.transferKey).isEqualTo(fakeTransferKeyGenerator.generate())
        assertThat(transfer.sourceAccountId).isEqualTo(sourceAccountId)
        assertThat(transfer.recipientBankCode).isEqualTo(recipientBankCode)
        assertThat(transfer.recipientAccountNumber).isEqualTo(recipientAccountNumber)
        assertThat(transfer.amount).isEqualTo(amount.value)
        assertThat(transfer.state).isEqualTo(TransferState.TRANSFERRING)
        assertThat(transaction.transferKey).isEqualTo(transfer.transferKey)
        assertThat(transaction.accountId).isEqualTo(sourceAccountId)
        assertThat(transaction.otherBankCode).isEqualTo(recipientBankCode)
        assertThat(transaction.otherAccountNumber).isEqualTo(recipientAccountNumber)
        assertThat(transaction.amount).isEqualTo(-amount.value)
        assertThat(transaction.type).isEqualTo(AccountTransactionType.DEBIT)
    }

    @Test
    fun `출금 계좌가 없으면 예외를 던지고 거래를 기록하지 않는다`() {
        given(accountRepository.findById(sourceAccountId)).willReturn(Optional.empty())

        assertThatThrownBy {
            transferService.initiateTransfer(
                customerId,
                sourceAccountId,
                transferRequest,
            )
        }.isInstanceOf(AccountNotFoundException::class.java)

        verifyNoInteractions(accountTransactionRepository, transferRepository)
    }

    @Test
    fun `다른 고객의 계좌이면 잔액을 변경하거나 거래를 기록하지 않는다`() {
        val account = Account.create("other-account-number", 2L, "다른 고객 계좌")
        account.credit(PositiveAmount(30_000L))
        given(accountRepository.findById(sourceAccountId)).willReturn(Optional.of(account))

        assertThatThrownBy {
            transferService.initiateTransfer(
                customerId,
                sourceAccountId,
                transferRequest,
            )
        }.isInstanceOf(AccountNotFoundException::class.java)

        assertThat(account.balance).isEqualTo(30_000L)
        verifyNoInteractions(accountTransactionRepository, transferRepository)
    }

    @Test
    fun `잔액이 부족하면 잔액을 변경하거나 거래를 기록하지 않는다`() {
        val account = Account.create("source-account-number", customerId, "출금 계좌")
        account.credit(PositiveAmount(5_000L))
        given(accountRepository.findById(sourceAccountId)).willReturn(Optional.of(account))

        assertThatThrownBy {
            transferService.initiateTransfer(
                customerId,
                sourceAccountId,
                transferRequest,
            )
        }.isInstanceOf(InsufficientBalanceException::class.java)

        assertThat(account.balance).isEqualTo(5_000L)
        verifyNoInteractions(accountTransactionRepository, transferRepository)
    }

    @Test
    fun `같은 은행 송금은 계좌를 조회하거나 거래를 기록하지 않고 거절한다`() {
        assertThatThrownBy {
            transferService.initiateTransfer(
                customerId,
                sourceAccountId,
                transferRequest.copy(recipientBankCode = bankCode),
            )
        }.isInstanceOf(SameBankTransferNotSupportException::class.java)

        verifyNoInteractions(accountRepository, accountTransactionRepository, transferRepository)
    }
}

class FakeTransferKeyGenerator : TransferKeyGenerator {
    override fun generate(): String = "transfer_key"
}
