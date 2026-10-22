package org.financialsystem.bank.receipt

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@IntegrationTest
@ActiveProfiles("integration-test", "bank-b")
@AutoConfigureMockMvc
class ReceiptScenarioIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var receiptRepository: ReceiptRepository

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var accountTransactionRepository: AccountTransactionRepository

    @Test
    @Sql("/sql/receipt/S-301.sql")
    fun `S-301 결제완료 결과를 통지받으면 입금하고 수취를 성공 처리한다`() {
        // when
        mockMvc
            .post("/settlement-results/E-301/settled")
            .andExpect {
                status { isNoContent() }
            }

        // then
        val receipt = receiptRepository.findAll().single()
        assertThat(receipt.state).isEqualTo(ReceiptState.SUCCEEDED)

        val account = accountRepository.findByAccountNumber("B-001")
        assertThat(account?.balance).isEqualTo(110_000L)

        val transaction = accountTransactionRepository.findAll().single()
        assertThat(transaction.accountId).isEqualTo(1L)
        assertThat(transaction.transferKey).isEqualTo("E-301")
        assertThat(transaction.type).isEqualTo(AccountTransactionType.CREDIT)
        assertThat(transaction.amount).isEqualTo(10_000L)
        assertThat(transaction.otherBankCode).isEqualTo("BANK_A")
        assertThat(transaction.otherAccountNumber).isEqualTo("A-001")
    }

    @Test
    @Sql("/sql/receipt/S-302.sql")
    fun `S-302 결제거절 결과를 통지받으면 입금 없이 수취를 실패 처리한다`() {
        // when
        mockMvc
            .post("/settlement-results/E-302/rejected")
            .andExpect {
                status { isNoContent() }
            }

        // then
        val receipt = receiptRepository.findAll().single()
        assertThat(receipt.state).isEqualTo(ReceiptState.FAILED)

        val account = accountRepository.findByAccountNumber("B-001")
        assertThat(account?.balance).isEqualTo(100_000L)
        assertThat(accountTransactionRepository.findAll()).isEmpty()
    }
}
