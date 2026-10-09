package org.financialsystem.bank.receipt.application

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.ReceiptFixtures
import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.settlement.application.SETTLEMENT_TIME_LIMIT
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesRequest
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesResponse
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
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
class ReceiptRecoveryServiceTest {
    @Mock
    lateinit var receiptRepository: ReceiptRepository

    @Mock
    lateinit var settlementClient: SettlementClient

    @Mock
    lateinit var accountRepository: AccountRepository

    @Mock
    lateinit var accountTransactionRepository: AccountTransactionRepository

    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val transferCreatedAt = now.minus(SETTLEMENT_TIME_LIMIT).minusSeconds(1)
    private val bankCode = "BANK_B"
    private val account =
        Account.create("recipient-account", 1L, "수취 계좌").apply {
            ReflectionTestUtils.setField(this, "id", 10L)
            credit(PositiveAmount(20_000L))
        }

    private lateinit var receiptRecoveryService: ReceiptRecoveryService

    @BeforeEach
    fun setUp() {
        receiptRecoveryService =
            ReceiptRecoveryService(
                clock = Clock.fixed(now, ZoneOffset.UTC),
                bankCode = bankCode,
                receiptRepository = receiptRepository,
                settlementClient = settlementClient,
                accountRepository = accountRepository,
                accountTransactionRepository = accountTransactionRepository,
                transactionOperations = TransactionOperations.withoutTransaction(),
            )
    }

    @Nested
    inner class `수취를 복구한다` {
        @Test
        fun `결제가 완료되면 입금 내역을 기록하고 수취를 성공 처리한다`() {
            // given
            val receipt =
                ReceiptFixtures.receipt(
                    transferKey = "transfer-key",
                    recipientAccountId = account.id!!,
                    transferCreatedAt = transferCreatedAt,
                    createdAt = now.minusSeconds(10),
                )
            val transferKeys = listOf(receipt.transferKey)
            val request = BatchGetSettlementStatesRequest(bankCode, transferKeys)
            val initialBalance = account.balance

            given(receiptRepository.findRecoveryTargets(any(), any(), any(), any(), any(), any()))
                .willReturn(listOf(receipt), emptyList())
            given(settlementClient.batchGetSettlementStates(request))
                .willReturn(
                    BatchGetSettlementStatesResponse(
                        mapOf(receipt.transferKey to SettlementState.SETTLED),
                    ),
                )
            given(receiptRepository.findAllByTransferKeyIn(transferKeys))
                .willReturn(listOf(receipt))
            given(accountRepository.findAllById(listOf(account.id!!)))
                .willReturn(listOf(account))

            // when
            receiptRecoveryService.recoverUnresolvedReceipts()

            // then
            assertThat(receipt.state).isEqualTo(ReceiptState.SUCCEEDED)
            assertThat(account.balance).isEqualTo(initialBalance + receipt.amount)

            val transactionCaptor = argumentCaptor<AccountTransaction>()
            verify(accountTransactionRepository).save(transactionCaptor.capture())
            val credit = transactionCaptor.firstValue

            assertThat(credit.type).isEqualTo(AccountTransactionType.CREDIT)
            assertThat(credit.transferKey).isEqualTo(receipt.transferKey)
            assertThat(credit.accountId).isEqualTo(receipt.recipientAccountId)
            assertThat(credit.amount).isEqualTo(receipt.amount)
            assertThat(credit.otherBankCode).isEqualTo(receipt.sourceBankCode)
            assertThat(credit.otherAccountNumber).isEqualTo(receipt.sourceAccountNumber)
        }

        @ParameterizedTest
        @EnumSource(value = SettlementState::class, names = ["REJECTED"])
        @NullSource
        fun `결제가 거절되거나 중앙 기록이 없으면 입금 없이 수취를 실패 처리한다`(state: SettlementState?) {
            // given
            val receipt =
                ReceiptFixtures.receipt(
                    transferKey = "transfer-key",
                    recipientAccountId = account.id!!,
                    transferCreatedAt = transferCreatedAt,
                    createdAt = now.minusSeconds(10),
                )
            val transferKeys = listOf(receipt.transferKey)
            val request = BatchGetSettlementStatesRequest(bankCode, transferKeys)
            val initialBalance = account.balance

            given(receiptRepository.findRecoveryTargets(any(), any(), any(), any(), any(), any()))
                .willReturn(listOf(receipt), emptyList())
            given(settlementClient.batchGetSettlementStates(request))
                .willReturn(
                    BatchGetSettlementStatesResponse(
                        states = if (state == null) emptyMap() else mapOf(receipt.transferKey to state),
                    ),
                )
            given(receiptRepository.findAllByTransferKeyIn(transferKeys))
                .willReturn(listOf(receipt))
            given(accountRepository.findAllById(listOf(account.id!!)))
                .willReturn(listOf(account))

            // when
            receiptRecoveryService.recoverUnresolvedReceipts()

            // then
            assertThat(receipt.state).isEqualTo(ReceiptState.FAILED)
            assertThat(account.balance).isEqualTo(initialBalance)
            verifyNoInteractions(accountTransactionRepository)
        }

        @Test
        fun `수취 검증 대기 중이면 수취중 상태와 잔액을 유지한다`() {
            // given
            val receipt =
                ReceiptFixtures.receipt(
                    transferKey = "transfer-key",
                    recipientAccountId = account.id!!,
                    transferCreatedAt = transferCreatedAt,
                    createdAt = now.minusSeconds(10),
                )
            val transferKeys = listOf(receipt.transferKey)
            val request = BatchGetSettlementStatesRequest(bankCode, transferKeys)
            val initialBalance = account.balance

            given(receiptRepository.findRecoveryTargets(any(), any(), any(), any(), any(), any()))
                .willReturn(listOf(receipt), emptyList())
            given(settlementClient.batchGetSettlementStates(request))
                .willReturn(
                    BatchGetSettlementStatesResponse(
                        mapOf(receipt.transferKey to SettlementState.RECIPIENT_VALIDATION_PENDING),
                    ),
                )
            given(receiptRepository.findAllByTransferKeyIn(transferKeys))
                .willReturn(listOf(receipt))
            given(accountRepository.findAllById(listOf(account.id!!)))
                .willReturn(listOf(account))

            // when
            receiptRecoveryService.recoverUnresolvedReceipts()

            // then
            assertThat(receipt.state).isEqualTo(ReceiptState.RECEIVING)
            assertThat(account.balance).isEqualTo(initialBalance)
            verifyNoInteractions(accountTransactionRepository)
        }

        @Test
        fun `중앙 결제 조회가 실패하면 입금하지 않고 다음 실행에서 다시 조회한다`() {
            // given
            val receipt =
                ReceiptFixtures.receipt(
                    transferKey = "transfer-key",
                    recipientAccountId = account.id!!,
                    transferCreatedAt = transferCreatedAt,
                    createdAt = now.minusSeconds(10),
                )
            val transferKeys = listOf(receipt.transferKey)
            val request = BatchGetSettlementStatesRequest(bankCode, transferKeys)
            val initialBalance = account.balance
            val exception = SettlementClientException("중앙 결제 조회 실패")

            given(receiptRepository.findRecoveryTargets(any(), any(), any(), any(), any(), any()))
                .willReturn(listOf(receipt))
            given(settlementClient.batchGetSettlementStates(request))
                .willThrow(exception)

            // when
            assertFailsWith<SettlementClientException> { receiptRecoveryService.recoverUnresolvedReceipts() }
            assertFailsWith<SettlementClientException> { receiptRecoveryService.recoverUnresolvedReceipts() }

            // then
            assertThat(receipt.state).isEqualTo(ReceiptState.RECEIVING)
            assertThat(account.balance).isEqualTo(initialBalance)
            verify(settlementClient, times(2)).batchGetSettlementStates(request)
            verifyNoInteractions(accountRepository, accountTransactionRepository)
        }
    }
}
