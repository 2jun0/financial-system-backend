package org.financialsystem.bank.transfer

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.customer.application.CustomerPrincipal
import org.financialsystem.bank.support.IntegrationTest
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import org.wiremock.spring.InjectWireMock
import org.wiremock.spring.WireMockConfigurationCustomizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

@SpringBootTest
@IntegrationTest
@AutoConfigureMockMvc
@EnableWireMock(
    ConfigureWireMock(
        baseUrlProperties = ["settlement.base-url"],
        configurationCustomizers = [TransferConcurrencyIntegrationTest.SettlementWireMockConfig::class],
    ),
)
class TransferConcurrencyIntegrationTest {
    @InjectWireMock
    lateinit var settlementServer: WireMockServer

    @Autowired
    lateinit var mockMvc: MockMvc

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
        val principal = CustomerPrincipal(1L, "customer", "password-hash")
        val requestCount = 20
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)
        settlementServer.stubFor(
            WireMock
                .post(urlEqualTo("/settlements"))
                .willReturn(
                    okJson(
                        """
                        {
                            "transferKey": "{{jsonPath request.body '$.transferKey'}}",
                            "state": "SETTLED"
                        }
                        """.trimIndent(),
                    ).withTransformers("response-template"),
                ),
        )

        // when
        val responses =
            Executors.newFixedThreadPool(requestCount).use { executor ->
                val futures =
                    List(requestCount) {
                        executor.submit<MockHttpServletResponse> {
                            ready.countDown()
                            check(start.await(10, SECONDS)) { "동시 송금 시작 신호를 받지 못했습니다." }
                            mockMvc
                                .post("/account/A-001/transfer") {
                                    with(user(principal))
                                    contentType = MediaType.APPLICATION_JSON
                                    content =
                                        """
                                        {
                                            "recipientBankCode": "BANK_B",
                                            "recipientAccountNumber": "B-001",
                                            "amount": 10000
                                        }
                                        """.trimIndent()
                                }.andReturn()
                                .response
                        }
                    }
                assertThat(ready.await(10, SECONDS)).isTrue()
                start.countDown()
                futures.map { it.get(30, SECONDS) }
            }

        // then
        assertThat(responses.count { it.status == HttpStatus.OK.value() }).isEqualTo(10)
        assertThat(responses.count { it.status == HttpStatus.CONFLICT.value() }).isEqualTo(10)
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

    class SettlementWireMockConfig : WireMockConfigurationCustomizer {
        override fun customize(
            configuration: WireMockConfiguration,
            options: ConfigureWireMock,
        ) {
            // JDK HTTP 클라이언트의 chunked 요청에서 HTTP/2 전환 시 본문 유실을 방지한다.
            // https://github.com/wiremock/wiremock/issues/3577
            configuration.http2PlainDisabled(true)
        }
    }
}
