package org.financialsystem.bank.transfer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesResponse
import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementResponse
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.support.IntegrationTest
import org.financialsystem.bank.transfer.application.TransferRecoveryService
import org.financialsystem.bank.transfer.application.TransferService
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRecoveryScheduler
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.financialsystem.bank.transfer.presentation.dto.TransferResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest
@IntegrationTest
class TransferRecoveryConcurrencyIntegrationTest {
    @MockitoBean
    lateinit var settlementClient: SettlementClient

    @MockitoBean
    lateinit var recoveryScheduler: TransferRecoveryScheduler

    @Autowired
    lateinit var recoveryService: TransferRecoveryService

    @Autowired
    lateinit var transferService: TransferService

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var transferRepository: TransferRepository

    @Autowired
    lateinit var transactionRepository: AccountTransactionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    @Sql("/sql/transfer/concurrent-recovery.sql")
    fun `같은 송금을 동시에 복구해도 환불은 한 번만 반영한다`() {
        // given
        val ready = CountDownLatch(2)
        whenever(settlementClient.batchGetSettlementStates(any())).thenAnswer {
            ready.countDown()
            check(ready.await(10, SECONDS)) { "두 복구 요청이 같은 송금을 조회하지 못했습니다." }
            BatchGetSettlementStatesResponse(mapOf("E-RECOVERY" to SettlementState.REJECTED))
        }

        // when
        Executors.newFixedThreadPool(2).use { executor ->
            val futures = List(2) { executor.submit<Unit> { recoveryService.recoverUnresolvedTransfers() } }
            futures.forEach { it.get(30, SECONDS) }
        }

        // then
        assertThat(accountRepository.findByAccountNumber("A-001")?.balance).isEqualTo(100_000L)
        assertThat(transferRepository.findByTransferKey("E-RECOVERY")?.state).isEqualTo(TransferState.FAILED)
        val records = transactionRepository.findAll()
        assertThat(records.map { it.transferKey }).containsOnly("E-RECOVERY")
        assertThat(records.map { it.type to it.amount }).containsExactlyInAnyOrder(
            AccountTransactionType.DEBIT to -10_000L,
            AccountTransactionType.COMPENSATION to 10_000L,
        )
        assertThat(records.sumOf { it.amount }).isZero()
    }

    @ParameterizedTest
    @EnumSource(value = SettlementState::class, names = ["SETTLED", "REJECTED"])
    @Sql("/sql/transfer/concurrent-transfers.sql")
    fun `복구 완료 후 중앙 응답이 도착해도 송금 결과를 다시 반영하지 않는다`(state: SettlementState) {
        // given
        val requested = CountDownLatch(1)
        val recovered = CountDownLatch(1)
        whenever(settlementClient.requestSettlement(any())).thenAnswer {
            val request = it.getArgument<SettlementRequest>(0)
            requested.countDown()
            check(recovered.await(10, SECONDS))
            SettlementResponse(request.transferKey, state)
        }
        whenever(settlementClient.batchGetSettlementStates(any())).thenAnswer {
            val transferKey = transferRepository.findAll().single().transferKey
            BatchGetSettlementStatesResponse(mapOf(transferKey to state))
        }

        // when
        val response =
            Executors.newSingleThreadExecutor().use { executor ->
                val normal =
                    executor.submit<TransferResponse> {
                        transferService.transfer(1L, "A-001", TransferRequest("BANK_B", "B-001", PositiveAmount(10_000)))
                    }
                try {
                    check(requested.await(10, SECONDS))
                    jdbcTemplate.update("UPDATE transfers SET created_at = UTC_TIMESTAMP() - INTERVAL 10 MINUTE")
                    recoveryService.recoverUnresolvedTransfers()
                    recovered.countDown()
                    normal.get(30, SECONDS)
                } finally {
                    recovered.countDown()
                }
            }

        // then
        val transfer = transferRepository.findAll().single()
        val records = transactionRepository.findAll()
        assertThat(response.transferKey).isEqualTo(transfer.transferKey)
        assertThat(records.map { it.transferKey }).containsOnly(transfer.transferKey)
        if (state == SettlementState.SETTLED) {
            assertThat(response.state).isEqualTo(TransferState.SUCCEEDED)
            assertThat(transfer.state).isEqualTo(TransferState.SUCCEEDED)
            assertThat(accountRepository.findByAccountNumber("A-001")?.balance).isEqualTo(90_000L)
            assertThat(records.map { it.type to it.amount }).containsExactly(AccountTransactionType.DEBIT to -10_000L)
        } else {
            assertThat(response.state).isEqualTo(TransferState.FAILED)
            assertThat(transfer.state).isEqualTo(TransferState.FAILED)
            assertThat(accountRepository.findByAccountNumber("A-001")?.balance).isEqualTo(100_000L)
            assertThat(records.map { it.type to it.amount }).containsExactlyInAnyOrder(
                AccountTransactionType.DEBIT to -10_000L,
                AccountTransactionType.COMPENSATION to 10_000L,
            )
            assertThat(records.sumOf { it.amount }).isZero()
        }
    }

    @Test
    @Sql("/sql/transfer/concurrent-recovery.sql")
    fun `보상 기록 저장이 실패하면 환불 잔액과 송금 상태도 롤백한다`() {
        // given
        jdbcTemplate.update(
            """
            INSERT INTO account_transactions (account_id, transfer_key, other_bank_code, other_account_number, amount, type, created_at)
            VALUES (1, 'E-RECOVERY', 'BANK_B', 'B-001', 10000, 'COMPENSATION', UTC_TIMESTAMP())
            """.trimIndent(),
        )
        whenever(settlementClient.batchGetSettlementStates(any())).thenReturn(
            BatchGetSettlementStatesResponse(mapOf("E-RECOVERY" to SettlementState.REJECTED)),
        )

        // when
        assertThatThrownBy { recoveryService.recoverUnresolvedTransfers() }.isInstanceOf(DataAccessException::class.java)

        // then
        assertThat(accountRepository.findByAccountNumber("A-001")?.balance).isEqualTo(90_000L)
        assertThat(transferRepository.findByTransferKey("E-RECOVERY")?.state).isEqualTo(TransferState.TRANSFERRING)
        assertThat(transactionRepository.findAll()).hasSize(2)
    }
}
