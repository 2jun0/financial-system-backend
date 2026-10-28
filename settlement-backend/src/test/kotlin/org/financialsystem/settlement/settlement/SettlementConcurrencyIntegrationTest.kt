package org.financialsystem.settlement.settlement

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.RecipientBankClient
import org.financialsystem.settlement.settlement.application.SettlementService
import org.financialsystem.settlement.settlement.application.dto.SettlementRequest
import org.financialsystem.settlement.settlement.application.dto.SettlementResponse
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementResponse
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.financialsystem.settlement.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest
@IntegrationTest
class SettlementConcurrencyIntegrationTest {
    @MockitoBean
    lateinit var recipientBankClient: RecipientBankClient

    @Autowired
    lateinit var settlementService: SettlementService

    @Autowired
    lateinit var bankRepository: BankRepository

    @Autowired
    lateinit var settlementRepository: SettlementRepository

    @Test
    @Sql("/sql/settlement/concurrent-settlements.sql")
    fun `같은 은행에서 동시에 결제해도 결제잔액을 초과해 이동하지 않는다`() {
        // given
        val requestCount = 20
        val transferCreatedAt = Instant.now()
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)
        whenever(recipientBankClient.validateSettlement(any(), any())).thenReturn(ValidateSettlementResponse(true))

        // when
        val responses =
            Executors.newFixedThreadPool(requestCount).use { executor ->
                val futures =
                    List(requestCount) { index ->
                        executor.submit<SettlementResponse> {
                            ready.countDown()
                            check(start.await(10, SECONDS)) { "동시 결제 시작 신호를 받지 못했습니다." }
                            settlementService.requestSettlement(
                                SettlementRequest(
                                    transferKey = "E-CONCURRENT-${index + 1}",
                                    sourceBankCode = "BANK_A",
                                    sourceAccountNumber = "A-001",
                                    recipientBankCode = "BANK_B",
                                    recipientAccountNumber = "B-001",
                                    amount = PositiveAmount(10_000),
                                    transferCreatedAt = transferCreatedAt,
                                ),
                            )
                        }
                    }
                assertThat(ready.await(10, SECONDS)).isTrue()
                start.countDown()
                futures.map { it.get(30, SECONDS) }
            }

        // then
        val responseStates = responses.map { it.state }
        assertThat(responseStates.count { it == SettlementState.SETTLED }).isEqualTo(10)
        assertThat(responseStates.count { it == SettlementState.REJECTED }).isEqualTo(10)

        val settlements = settlementRepository.findAll()
        assertThat(settlements).hasSize(requestCount)
        assertThat(settlements.map { it.transferKey }.toSet()).isEqualTo((1..requestCount).map { "E-CONCURRENT-$it" }.toSet())
        assertThat(settlements.count { it.state == SettlementState.SETTLED }).isEqualTo(10)
        assertThat(settlements.count { it.state == SettlementState.REJECTED }).isEqualTo(10)
        settlements.forEach { settlement ->
            assertThat(settlement.sourceBankId).isEqualTo(1L)
            assertThat(settlement.recipientBankId).isEqualTo(2L)
            assertThat(settlement.amount).isEqualTo(10_000L)
        }

        val sourceBalance = bankRepository.findByBankCode("BANK_A")?.balance
        val recipientBalance = bankRepository.findByBankCode("BANK_B")?.balance
        assertThat(sourceBalance).isEqualTo(0L)
        assertThat(recipientBalance).isEqualTo(1_100_000L)
        assertThat(bankRepository.findAll().sumOf { it.balance }).isEqualTo(1_100_000L)
    }

    @Test
    @Sql("/sql/settlement/concurrent-settlements.sql")
    fun `서로 반대 방향으로 동시에 결제해도 모든 결제를 반영한다`() {
        // given
        val requestCount = 20
        val transferCreatedAt = Instant.now()
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)
        whenever(recipientBankClient.validateSettlement(any(), any())).thenReturn(ValidateSettlementResponse(true))

        // when
        val responses =
            Executors.newFixedThreadPool(requestCount).use { executor ->
                val futures =
                    List(requestCount) { index ->
                        executor.submit<SettlementResponse> {
                            ready.countDown()
                            check(start.await(10, SECONDS)) { "동시 결제 시작 신호를 받지 못했습니다." }
                            settlementService.requestSettlement(
                                SettlementRequest(
                                    transferKey = "E-BIDIRECTIONAL-${index + 1}",
                                    sourceBankCode = if (index % 2 == 0) "BANK_A" else "BANK_B",
                                    sourceAccountNumber = if (index % 2 == 0) "A-001" else "B-001",
                                    recipientBankCode = if (index % 2 == 0) "BANK_B" else "BANK_A",
                                    recipientAccountNumber = if (index % 2 == 0) "B-001" else "A-001",
                                    amount = PositiveAmount(10_000),
                                    transferCreatedAt = transferCreatedAt,
                                ),
                            )
                        }
                    }
                assertThat(ready.await(10, SECONDS)).isTrue()
                start.countDown()
                futures.map { it.get(30, SECONDS) }
            }

        // then
        val responseStates = responses.map { it.state }
        assertThat(responseStates.count { it == SettlementState.SETTLED }).isEqualTo(requestCount)
        assertThat(responseStates.count { it == SettlementState.REJECTED }).isEqualTo(0)

        val settlements = settlementRepository.findAll()
        assertThat(settlements).hasSize(requestCount)
        assertThat(settlements.map { it.transferKey }.toSet()).isEqualTo((1..requestCount).map { "E-BIDIRECTIONAL-$it" }.toSet())
        assertThat(settlements.count { it.state == SettlementState.SETTLED }).isEqualTo(requestCount)
        assertThat(settlements.count { it.state == SettlementState.REJECTED }).isEqualTo(0)
        settlements.forEach { settlement ->
            assertThat(settlement.amount).isEqualTo(10_000L)
        }

        val sourceBalance = bankRepository.findByBankCode("BANK_A")?.balance
        val recipientBalance = bankRepository.findByBankCode("BANK_B")?.balance
        assertThat(sourceBalance).isEqualTo(100_000L)
        assertThat(recipientBalance).isEqualTo(1_000_000L)
        assertThat(bankRepository.findAll().sumOf { it.balance }).isEqualTo(1_100_000L)
    }
}
