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
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementResponse
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionOperations
import java.util.Optional
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class TransferServiceTest {
    @Mock
    lateinit var accountRepository: AccountRepository

    @Mock
    lateinit var transferRepository: TransferRepository

    @Mock
    lateinit var accountTransactionRepository: AccountTransactionRepository

    @Mock
    lateinit var settlementClient: SettlementClient

    private val sourceAccountNumber = "001-source-account"
    private val fakeTransferKeyGenerator = FakeTransferKeyGenerator()
    private val bankCode = "BANK_A"
    private val customerId = 1L
    private val sourceAccountId = 10L
    private val recipientBankCode = "BANK_B"
    private val recipientAccountNumber = "recipient-account-number"
    private val amount = PositiveAmount(10_000L)
    private val account =
        Account.create(sourceAccountNumber, customerId, "출금 계좌").apply {
            ReflectionTestUtils.setField(this, "id", sourceAccountId)
            credit(PositiveAmount(30_000L))
        }

    private lateinit var transferService: TransferService

    @BeforeEach
    fun setUp() {
        transferService =
            TransferService(
                transferKeyGenerator = fakeTransferKeyGenerator,
                accountRepository = accountRepository,
                transferRepository = transferRepository,
                accountTransactionRepository = accountTransactionRepository,
                settlementClient = settlementClient,
                transactionOperations = TransactionOperations.withoutTransaction(),
                bankCode = bankCode,
            )
    }

    @Nested
    inner class `송금 한다` {
        val transferKey = fakeTransferKeyGenerator.stubTransferKey
        val transferRequest =
            TransferRequest(
                recipientBankCode = recipientBankCode,
                recipientAccountNumber = recipientAccountNumber,
                amount = amount,
            )
        val settlementRequest =
            SettlementRequest(
                transferKey = transferKey,
                sourceBankCode = bankCode,
                sourceAccountNumber = sourceAccountNumber,
                recipientBankCode = recipientBankCode,
                recipientAccountNumber = recipientAccountNumber,
                amount = amount,
            )

        @Test
        fun `성공시 출금 내역을 기록하고 송금 성공 상태를 반영한다`() {
            // given
            val initialBalance = account.balance
            lateinit var savedTransfer: Transfer
            given(accountRepository.findByAccountNumber(any())).willReturn(account)
            given(transferRepository.save(any<Transfer>())).willAnswer { invocation ->
                invocation.getArgument<Transfer>(0).also {
                    savedTransfer = it
                }
            }
            given(transferRepository.findByTransferKey(any())).willAnswer { savedTransfer }
            given(settlementClient.requestSettlement(any()))
                .willReturn(SettlementResponse(transferKey, SettlementState.SETTLED))

            // when
            val response = transferService.transfer(customerId, sourceAccountNumber, transferRequest)

            // then
            val transactionCaptor = ArgumentCaptor.forClass(AccountTransaction::class.java)
            verify(accountTransactionRepository).save(transactionCaptor.capture())
            val transaction = transactionCaptor.value
            val transferCaptor = ArgumentCaptor.forClass(Transfer::class.java)
            verify(transferRepository).save(transferCaptor.capture())
            val transfer = transferCaptor.value

            assertThat(response.transferKey).isEqualTo(transferKey)
            assertThat(response.state).isEqualTo(TransferState.SUCCEEDED)

            assertThat(account.balance).isEqualTo(initialBalance - amount.value)

            assertThat(transfer.transferKey).isEqualTo(transferKey)
            assertThat(transfer.sourceAccountId).isEqualTo(sourceAccountId)
            assertThat(transfer.recipientBankCode).isEqualTo(recipientBankCode)
            assertThat(transfer.recipientAccountNumber).isEqualTo(recipientAccountNumber)
            assertThat(transfer.amount).isEqualTo(amount.value)
            assertThat(transfer.state).isEqualTo(TransferState.SUCCEEDED)

            assertThat(transaction.transferKey).isEqualTo(transferKey)
            assertThat(transaction.accountId).isEqualTo(sourceAccountId)
            assertThat(transaction.otherBankCode).isEqualTo(recipientBankCode)
            assertThat(transaction.otherAccountNumber).isEqualTo(recipientAccountNumber)
            assertThat(transaction.amount).isEqualTo(-amount.value)
            assertThat(transaction.type).isEqualTo(AccountTransactionType.DEBIT)
        }

        @Test
        fun `결제가 거절되면 잔액을 복구하고 보상 내역과 송금 실패 상태를 반영한다`() {
            // given
            val initialBalance = account.balance
            lateinit var savedTransfer: Transfer
            given(accountRepository.findByAccountNumber(any())).willReturn(account)
            given(transferRepository.save(any<Transfer>())).willAnswer { invocation ->
                invocation.getArgument<Transfer>(0).also { savedTransfer = it }
            }
            given(transferRepository.findByTransferKey(any())).willAnswer { savedTransfer }
            given(accountRepository.findById(any())).willReturn(Optional.of(account))
            given(settlementClient.requestSettlement(any()))
                .willReturn(SettlementResponse(transferKey, SettlementState.REJECTED))

            // when
            val response = transferService.transfer(customerId, sourceAccountNumber, transferRequest)

            // then
            val transactionCaptor = ArgumentCaptor.forClass(AccountTransaction::class.java)
            verify(accountTransactionRepository, times(2)).save(transactionCaptor.capture())
            val compensation = transactionCaptor.allValues.single { it.type == AccountTransactionType.COMPENSATION }

            assertThat(response.transferKey).isEqualTo(transferKey)
            assertThat(response.state).isEqualTo(TransferState.FAILED)

            assertThat(savedTransfer.state).isEqualTo(TransferState.FAILED)

            assertThat(account.balance).isEqualTo(initialBalance)

            assertThat(compensation.transferKey).isEqualTo(transferKey)
            assertThat(compensation.accountId).isEqualTo(sourceAccountId)
            assertThat(compensation.otherBankCode).isEqualTo(recipientBankCode)
            assertThat(compensation.otherAccountNumber).isEqualTo(recipientAccountNumber)
            assertThat(compensation.amount).isEqualTo(amount.value)
        }

        @Test
        fun `결제 요청이 실패하면 송금중으로 유지한다`() {
            // given
            val initialBalance = account.balance
            given(accountRepository.findByAccountNumber(any())).willReturn(account)
            doThrow(
                SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", RuntimeException("응답 시간 초과")),
            ).`when`(settlementClient).requestSettlement(settlementRequest)

            // when
            val response = transferService.transfer(customerId, sourceAccountNumber, transferRequest)

            // then
            val transferCaptor = ArgumentCaptor.forClass(Transfer::class.java)
            verify(transferRepository).save(transferCaptor.capture())
            val transfer = transferCaptor.value

            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(response.state).isEqualTo(TransferState.TRANSFERRING)

            assertThat(transfer.state).isEqualTo(TransferState.TRANSFERRING)
            assertThat(account.balance).isEqualTo(initialBalance - amount.value)
        }

        @Test
        fun `결제 요청 통신 예외가 아닌 예외는 그대로 전파한다`() {
            // given
            given(accountRepository.findByAccountNumber(any())).willReturn(account)
            doThrow(IllegalStateException("요청 직렬화 실패")).`when`(settlementClient).requestSettlement(settlementRequest)

            // when
            assertThatThrownBy {
                transferService.transfer(customerId, sourceAccountNumber, transferRequest)
            }
                // then
                .isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun `동일 은행 내 송금은 거절한다`() {
            // given
            val request =
                TransferRequest(
                    recipientBankCode = bankCode,
                    recipientAccountNumber = recipientAccountNumber,
                    amount = amount,
                )

            // when
            assertThatThrownBy {
                transferService.transfer(customerId, sourceAccountNumber, request)
            }
                // then
                .isInstanceOf(SameBankTransferNotSupportException::class.java)

            verifyNoInteractions(accountRepository, accountTransactionRepository, transferRepository, settlementClient)
        }

        @Test
        fun `출금 계좌가 없으면 예외를 던진다`() {
            // given
            given(accountRepository.findByAccountNumber(any())).willReturn(null)

            // when
            assertThatThrownBy {
                transferService.transfer(customerId, sourceAccountNumber, transferRequest)
            }
                // then
                .isInstanceOf(AccountNotFoundException::class.java)

            verifyNoInteractions(accountTransactionRepository, transferRepository, settlementClient)
        }

        @Test
        fun `송금 계좌가 본인 것이 아니면 예외를 던진다`() {
            // given
            val otherCustomerAccount = Account.create(sourceAccountNumber, 2L, "다른 고객 계좌")
            otherCustomerAccount.credit(PositiveAmount(30_000L))
            given(accountRepository.findByAccountNumber(any())).willReturn(otherCustomerAccount)

            // when
            assertThatThrownBy {
                transferService.transfer(customerId, sourceAccountNumber, transferRequest)
            }
                // then
                .isInstanceOf(AccountNotFoundException::class.java)

            assertThat(otherCustomerAccount.balance).isEqualTo(30_000L)
            verifyNoInteractions(accountTransactionRepository, transferRepository, settlementClient)
        }

        @Test
        fun `잔액이 부족하면 예외를 던진다`() {
            // given
            account.debit(PositiveAmount(25_000L))
            val initialBalance = account.balance
            given(accountRepository.findByAccountNumber(any())).willReturn(account)

            // when
            assertThatThrownBy {
                transferService.transfer(customerId, sourceAccountNumber, transferRequest)
            }
                // then
                .isInstanceOf(InsufficientBalanceException::class.java)

            assertThat(account.balance).isEqualTo(initialBalance)
            verifyNoInteractions(accountTransactionRepository, transferRepository, settlementClient)
        }
    }
}

class FakeTransferKeyGenerator : TransferKeyGenerator {
    val stubTransferKey = "transfer_key"

    override fun generate(): String = stubTransferKey
}
