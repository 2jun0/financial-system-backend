package org.financialsystem.bank.transfer.application

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.TransferFixtures
import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.SETTLEMENT_TIME_LIMIT
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesRequest
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesResponse
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class TransferRecoveryServiceTest {
    @Mock
    lateinit var transferRepository: TransferRepository

    @Mock
    lateinit var settlementClient: SettlementClient

    @Mock
    lateinit var accountRepository: AccountRepository

    @Mock
    lateinit var accountTransactionRepository: AccountTransactionRepository

    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val createdAtBefore = now.minus(SETTLEMENT_TIME_LIMIT)
    private val bankCode = "BANK_A"
    private val account =
        Account.create("source-account", 1L, "송금 계좌").apply {
            ReflectionTestUtils.setField(this, "id", 10L)
            credit(PositiveAmount(20_000L))
        }

    private lateinit var transferRecoveryService: TransferRecoveryService

    @BeforeEach
    fun setUp() {
        transferRecoveryService =
            TransferRecoveryService(
                clock = Clock.fixed(now, ZoneOffset.UTC),
                bankCode = bankCode,
                transferRepository = transferRepository,
                settlementClient = settlementClient,
                accountRepository = accountRepository,
                accountTransactionRepository = accountTransactionRepository,
                transactionOperations = TransactionOperations.withoutTransaction(),
            )
    }

    @Nested
    inner class `송금을 복구한다` {
        @Test
        fun `결제가 완료되면 추가 출금 없이 송금을 성공 처리한다`() {
            // given
            val transfer =
                TransferFixtures.transfer(
                    sourceAccountId = account.id!!,
                    createdAt = createdAtBefore.minusSeconds(1),
                )
            val transferKeys = listOf(transfer.transferKey)
            val initialBalance = account.balance
            given(transferRepository.findRecoveryTargets(any(), any(), any(), any(), any()))
                .willReturn(listOf(transfer), emptyList())
            given(settlementClient.batchGetSettlementStates(BatchGetSettlementStatesRequest(bankCode, transferKeys)))
                .willReturn(BatchGetSettlementStatesResponse(mapOf(transfer.transferKey to SettlementState.SETTLED)))
            given(transferRepository.findAllByTransferKeyIn(transferKeys)).willReturn(listOf(transfer))
            given(accountRepository.findAllById(listOf(account.id!!))).willReturn(listOf(account))

            // when
            transferRecoveryService.recoverUnresolvedTransfers()

            // then
            assertThat(transfer.state).isEqualTo(TransferState.SUCCEEDED)
            assertThat(account.balance).isEqualTo(initialBalance)
            verifyNoInteractions(accountTransactionRepository)
        }

        @ParameterizedTest
        @EnumSource(value = SettlementState::class, names = ["REJECTED"])
        @NullSource
        fun `결제가 거절되거나 중앙 기록이 없으면 출금을 보상하고 송금을 실패 처리한다`(state: SettlementState?) {
            // given
            val transfer =
                TransferFixtures.transfer(
                    sourceAccountId = account.id!!,
                    createdAt = createdAtBefore.minusSeconds(1),
                )
            val transferKeys = listOf(transfer.transferKey)
            val initialBalance = account.balance
            val states = if (state == null) emptyMap() else mapOf(transfer.transferKey to state)
            given(transferRepository.findRecoveryTargets(any(), any(), any(), any(), any()))
                .willReturn(listOf(transfer), emptyList())
            given(settlementClient.batchGetSettlementStates(BatchGetSettlementStatesRequest(bankCode, transferKeys)))
                .willReturn(BatchGetSettlementStatesResponse(states))
            given(transferRepository.findAllByTransferKeyIn(transferKeys)).willReturn(listOf(transfer))
            given(accountRepository.findAllById(listOf(account.id!!))).willReturn(listOf(account))

            // when
            transferRecoveryService.recoverUnresolvedTransfers()

            // then
            val transactionCaptor = argumentCaptor<AccountTransaction>()
            verify(accountTransactionRepository).save(transactionCaptor.capture())
            val compensation = transactionCaptor.firstValue

            assertThat(transfer.state).isEqualTo(TransferState.FAILED)
            assertThat(account.balance).isEqualTo(initialBalance + transfer.amount)
            assertThat(compensation.type).isEqualTo(AccountTransactionType.COMPENSATION)
            assertThat(compensation.transferKey).isEqualTo(transfer.transferKey)
            assertThat(compensation.accountId).isEqualTo(transfer.sourceAccountId)
            assertThat(compensation.amount).isEqualTo(transfer.amount)
            assertThat(compensation.otherBankCode).isEqualTo(transfer.recipientBankCode)
            assertThat(compensation.otherAccountNumber).isEqualTo(transfer.recipientAccountNumber)
        }

        @Test
        fun `수취 검증 대기 중이면 송금중 상태와 잔액을 유지한다`() {
            // given
            val transfer =
                TransferFixtures.transfer(
                    sourceAccountId = account.id!!,
                    createdAt = createdAtBefore.minusSeconds(1),
                )
            val transferKeys = listOf(transfer.transferKey)
            val initialBalance = account.balance
            given(transferRepository.findRecoveryTargets(any(), any(), any(), any(), any()))
                .willReturn(listOf(transfer), emptyList())
            given(settlementClient.batchGetSettlementStates(BatchGetSettlementStatesRequest(bankCode, transferKeys)))
                .willReturn(BatchGetSettlementStatesResponse(mapOf(transfer.transferKey to SettlementState.RECIPIENT_VALIDATION_PENDING)))
            given(transferRepository.findAllByTransferKeyIn(transferKeys)).willReturn(listOf(transfer))
            given(accountRepository.findAllById(listOf(account.id!!))).willReturn(listOf(account))

            // when
            transferRecoveryService.recoverUnresolvedTransfers()

            // then
            assertThat(transfer.state).isEqualTo(TransferState.TRANSFERRING)
            assertThat(account.balance).isEqualTo(initialBalance)
            verifyNoInteractions(accountTransactionRepository)
        }

        @Test
        fun `중앙 결제 조회가 실패하면 보상하지 않고 다음 실행에서 다시 조회한다`() {
            // given
            val transfer =
                TransferFixtures.transfer(
                    sourceAccountId = account.id!!,
                    createdAt = createdAtBefore.minusSeconds(1),
                )
            val transferKeys = listOf(transfer.transferKey)
            val initialBalance = account.balance
            val exception = SettlementClientException("중앙 결제 조회 실패")
            given(transferRepository.findRecoveryTargets(any(), any(), any(), any(), any()))
                .willReturn(listOf(transfer))
            given(settlementClient.batchGetSettlementStates(BatchGetSettlementStatesRequest(bankCode, transferKeys)))
                .willThrow(exception)

            // when
            assertFailsWith<SettlementClientException> { transferRecoveryService.recoverUnresolvedTransfers() }
            assertFailsWith<SettlementClientException> { transferRecoveryService.recoverUnresolvedTransfers() }

            // then
            assertThat(transfer.state).isEqualTo(TransferState.TRANSFERRING)
            assertThat(account.balance).isEqualTo(initialBalance)
            verify(settlementClient, times(2))
                .batchGetSettlementStates(BatchGetSettlementStatesRequest(bankCode, transferKeys))
            verifyNoInteractions(accountRepository, accountTransactionRepository)
        }
    }
}
