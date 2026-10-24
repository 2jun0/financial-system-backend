package org.financialsystem.bank.receipt

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import org.wiremock.spring.InjectWireMock
import java.time.Clock

@SpringBootTest
@IntegrationTest
@ActiveProfiles("integration-test", "bank-b")
@AutoConfigureMockMvc
@EnableWireMock(ConfigureWireMock(baseUrlProperties = ["settlement.base-url"]))
class ReceiptScenarioIntegrationTest {
    @InjectWireMock
    lateinit var settlementServer: WireMockServer

    @MockitoSpyBean
    lateinit var clock: Clock

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

    @Test
    @Sql("/sql/receipt/S-303.sql")
    fun `S-303 결제완료 결과 통지가 도달하지 않으면 만료 후 입금하고 수취를 성공 처리한다`() {
        // given
        settlementServer.stubFor(
            WireMock
                .get(urlPathEqualTo("/settlements:batchGetStates"))
                .withQueryParam("bankCode", equalTo("BANK_B"))
                .withQueryParam("transferKeys", equalTo("E-303"))
                .willReturn(
                    okJson(
                        """
                        {
                            "states": {
                                "E-303": "SETTLED"
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
        )

        // when
        val receipt = receiptRepository.findAll().single()
        val afterExpiration = receipt.transferCreatedAt.plusSeconds(301)
        doReturn(afterExpiration).whenever(clock).instant()
        Thread.sleep(1_000)

        // then
        val afterReceipt = receiptRepository.findAll().single()
        assertThat(afterReceipt.state).isEqualTo(ReceiptState.SUCCEEDED)

        val account = accountRepository.findByAccountNumber("B-001")
        assertThat(account?.balance).isEqualTo(110_000L)

        val transaction = accountTransactionRepository.findAll().single()
        assertThat(transaction.accountId).isEqualTo(1L)
        assertThat(transaction.transferKey).isEqualTo("E-303")
        assertThat(transaction.type).isEqualTo(AccountTransactionType.CREDIT)
        assertThat(transaction.amount).isEqualTo(10_000L)
        assertThat(transaction.otherBankCode).isEqualTo("BANK_A")
        assertThat(transaction.otherAccountNumber).isEqualTo("A-001")
    }

    @Test
    @Sql("/sql/receipt/S-304.sql")
    fun `S-304 결제거절 결과 통지가 도달하지 않으면 만료 후 입금 없이 수취를 실패 처리한다`() {
        // given
        settlementServer.stubFor(
            WireMock
                .get(urlPathEqualTo("/settlements:batchGetStates"))
                .withQueryParam("bankCode", equalTo("BANK_B"))
                .withQueryParam("transferKeys", equalTo("E-205"))
                .willReturn(
                    okJson(
                        """
                        {
                            "states": {
                                "E-205": "REJECTED"
                            }
                        }
                        """.trimIndent(),
                    ),
                ),
        )

        // when
        val receipt = receiptRepository.findAll().single()
        val afterExpiration = receipt.transferCreatedAt.plusSeconds(301)
        doReturn(afterExpiration).whenever(clock).instant()
        Thread.sleep(1_000)

        // then
        val afterReceipt = receiptRepository.findAll().single()
        assertThat(afterReceipt.state).isEqualTo(ReceiptState.FAILED)

        val account = accountRepository.findByAccountNumber("B-001")
        assertThat(account?.balance).isEqualTo(100_000L)
        assertThat(accountTransactionRepository.findAll()).isEmpty()
    }
}
