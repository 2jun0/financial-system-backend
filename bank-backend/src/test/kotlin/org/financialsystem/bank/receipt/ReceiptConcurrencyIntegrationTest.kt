package org.financialsystem.bank.receipt

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.settlement.application.SettlementResultService
import org.financialsystem.bank.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest
@IntegrationTest
@ActiveProfiles("integration-test", "bank-b")
class ReceiptConcurrencyIntegrationTest {
    @Autowired
    lateinit var settlementResultService: SettlementResultService

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var receiptRepository: ReceiptRepository

    @Autowired
    lateinit var accountTransactionRepository: AccountTransactionRepository

    @Test
    @Sql("/sql/receipt/concurrent-receipts.sql")
    fun `같은 계좌에 동시에 수취해도 모든 입금액을 반영한다`() {
        // given
        val requestCount = 20
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)

        // when
        Executors.newFixedThreadPool(requestCount).use { executor ->
            val futures =
                List(requestCount) { index ->
                    executor.submit {
                        ready.countDown()
                        check(start.await(10, SECONDS)) { "동시 수취 시작 신호를 받지 못했습니다." }
                        settlementResultService.applySettledResult("E-CONCURRENT-${index + 1}")
                    }
                }
            assertThat(ready.await(10, SECONDS)).isTrue()
            start.countDown()
            futures.forEach { it.get(30, SECONDS) }
        }

        // then
        assertThat(accountRepository.findByAccountNumber("B-001")?.balance).isEqualTo(300_000L)

        val receipts = receiptRepository.findAll()
        assertThat(receipts).hasSize(requestCount)
        assertThat(receipts.count { it.state == ReceiptState.SUCCEEDED }).isEqualTo(requestCount)

        val transactions = accountTransactionRepository.findAll()
        assertThat(transactions).hasSize(requestCount)
        assertThat(transactions.sumOf { it.amount }).isEqualTo(200_000L)
        assertThat(transactions.map { it.transferKey }.toSet()).isEqualTo(receipts.map { it.transferKey }.toSet())
        transactions.forEach { transaction ->
            assertThat(transaction.accountId).isEqualTo(1L)
            assertThat(transaction.type).isEqualTo(AccountTransactionType.CREDIT)
            assertThat(transaction.amount).isEqualTo(10_000L)
        }
    }
}
