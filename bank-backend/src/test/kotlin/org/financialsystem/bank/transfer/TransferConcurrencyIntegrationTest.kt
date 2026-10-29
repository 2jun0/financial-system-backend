package org.financialsystem.bank.transfer

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.domain.exception.InsufficientBalanceException
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementResponse
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.support.IntegrationTest
import org.financialsystem.bank.transfer.application.TransferService
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.financialsystem.bank.transfer.presentation.dto.TransferResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest
@IntegrationTest
class TransferConcurrencyIntegrationTest {
    @MockitoBean
    lateinit var settlementClient: SettlementClient

    @Autowired
    lateinit var transferService: TransferService

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var transferRepository: TransferRepository

    @Autowired
    lateinit var accountTransactionRepository: AccountTransactionRepository

    @Test
    @Sql("/sql/transfer/concurrent-transfers.sql")
    fun `같은 계좌에서 동시에 송금해도 잔액을 초과해 출금하지 않는다`() {
        // given
        val requestCount = 20
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)
        whenever(settlementClient.requestSettlement(any())).thenAnswer {
            val request = it.getArgument<SettlementRequest>(0)
            SettlementResponse(request.transferKey, SettlementState.SETTLED)
        }

        // when
        val responses =
            Executors.newFixedThreadPool(requestCount).use { executor ->
                val futures =
                    List(requestCount) {
                        executor.submit<Result<TransferResponse>> {
                            ready.countDown()
                            check(start.await(10, SECONDS)) { "동시 송금 시작 신호를 받지 못했습니다." }
                            runCatching {
                                transferService.transfer(
                                    customerId = 1L,
                                    sourceAccountNumber = "A-001",
                                    transferRequest = TransferRequest("BANK_B", "B-001", PositiveAmount(10_000)),
                                )
                            }
                        }
                    }
                assertThat(ready.await(10, SECONDS)).isTrue()
                start.countDown()
                futures.map { it.get(30, SECONDS) }
            }

        // then
        assertThat(responses.count { it.isSuccess }).isEqualTo(10)
        assertThat(responses.count { it.exceptionOrNull() is InsufficientBalanceException }).isEqualTo(10)
        assertThat(accountRepository.findByAccountNumber("A-001")?.balance).isEqualTo(0L)

        val transfers = transferRepository.findAll()
        assertThat(transfers).hasSize(10)
        transfers.forEach { transfer ->
            assertThat(transfer.state).isEqualTo(TransferState.SUCCEEDED)
            assertThat(transfer.sourceAccountId).isEqualTo(1L)
            assertThat(transfer.amount).isEqualTo(10_000L)
        }

        val transactions = accountTransactionRepository.findAll()
        assertThat(transactions).hasSize(10)
        assertThat(transactions.sumOf { it.amount }).isEqualTo(-100_000L)
        assertThat(transactions.map { it.transferKey }.toSet()).isEqualTo(transfers.map { it.transferKey }.toSet())
        transactions.forEach { transaction ->
            assertThat(transaction.type).isEqualTo(AccountTransactionType.DEBIT)
            assertThat(transaction.accountId).isEqualTo(1L)
            assertThat(transaction.amount).isEqualTo(-10_000L)
        }
    }

    @Test
    @Sql("/sql/transfer/concurrent-transfers.sql")
    fun `같은 계좌의 서로 다른 송금을 동시에 환불해도 모든 환불액을 반영한다`() {
        // given
        val requestCount = 20
        val settlementRequests = CountDownLatch(requestCount)
        whenever(settlementClient.requestSettlement(any())).thenAnswer {
            val request = it.getArgument<SettlementRequest>(0)
            settlementRequests.countDown()
            check(settlementRequests.await(10, SECONDS)) { "모든 송금의 출금이 완료되지 않았습니다." }
            SettlementResponse(request.transferKey, SettlementState.REJECTED)
        }

        // when
        val responses =
            Executors.newFixedThreadPool(requestCount).use { executor ->
                val futures =
                    List(requestCount) {
                        executor.submit<Result<TransferResponse>> {
                            runCatching {
                                transferService.transfer(
                                    customerId = 1L,
                                    sourceAccountNumber = "A-001",
                                    transferRequest = TransferRequest("BANK_B", "B-001", PositiveAmount(5_000)),
                                )
                            }
                        }
                    }
                futures.map { it.get(30, SECONDS) }
            }

        // then
        assertThat(responses.map { it.exceptionOrNull() }).containsOnlyNulls()
        assertThat(accountRepository.findByAccountNumber("A-001")?.balance).isEqualTo(100_000L)

        val transfers = transferRepository.findAll()
        assertThat(transfers).hasSize(requestCount)
        assertThat(transfers.map { it.transferKey }.toSet()).hasSize(requestCount)
        assertThat(transfers.map { it.state }).containsOnly(TransferState.FAILED)

        val transactions = accountTransactionRepository.findAll()
        assertThat(transactions).hasSize(requestCount * 2)
        assertThat(transactions.sumOf { it.amount }).isZero()
        transfers.forEach { transfer ->
            val records = transactions.filter { it.transferKey == transfer.transferKey }
            assertThat(records.map { it.accountId }).containsOnly(1L)
            assertThat(records.map { it.type to it.amount }).containsExactlyInAnyOrder(
                AccountTransactionType.DEBIT to -5_000L,
                AccountTransactionType.COMPENSATION to 5_000L,
            )
        }
    }
}
