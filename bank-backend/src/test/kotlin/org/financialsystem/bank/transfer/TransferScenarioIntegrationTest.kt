package org.financialsystem.bank.transfer

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.domain.AccountTransactionType
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.customer.application.CustomerPrincipal
import org.financialsystem.bank.support.IntegrationTest
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import org.wiremock.spring.InjectWireMock
import org.wiremock.spring.WireMockConfigurationCustomizer
import java.net.ServerSocket
import java.time.Clock

@SpringBootTest
@IntegrationTest
@AutoConfigureMockMvc
@EnableWireMock(
    ConfigureWireMock(
        baseUrlProperties = ["settlement.base-url"],
        configurationCustomizers = [TransferScenarioIntegrationTest.SettlementWireMockConfig::class],
    ),
)
class TransferScenarioIntegrationTest {
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

    @MockitoSpyBean
    lateinit var clock: Clock

    @BeforeEach
    fun startSettlementServer() {
        if (!settlementServer.isRunning) {
            settlementServer.start()
        }
    }

    @Test
    @Sql("/sql/transfer/S-101.sql")
    fun `S-101 중앙 결제완료 응답을 받으면 송금을 성공 처리한다`() {
        // given
        val principal = CustomerPrincipal(1L, "customer", "password-hash")
        settlementServer.stubFor(
            WireMock
                .post(urlEqualTo("/settlements"))
                .withRequestBody(
                    equalToJson(
                        """
                        {
                            "sourceBankCode": "BANK_A",
                            "sourceAccountNumber": "A-001",
                            "recipientBankCode": "BANK_B",
                            "recipientAccountNumber": "B-001",
                            "amount": 10000
                        }
                        """.trimIndent(),
                        true,
                        true,
                    ),
                ).willReturn(
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
        val result =
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
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.state") { value("SUCCEEDED") }
                }

        // then
        val transfer = transferRepository.findAll().single()
        result.andExpect {
            jsonPath("$.transferKey") { value(transfer.transferKey) }
        }
        assertThat(transfer.state).isEqualTo(TransferState.SUCCEEDED)

        val account = accountRepository.findByAccountNumber("A-001")
        assertThat(account?.balance).isEqualTo(90_000L)

        val transaction = accountTransactionRepository.findAll().single()
        assertThat(transaction.accountId).isEqualTo(1L)
        assertThat(transaction.transferKey).isEqualTo(transfer.transferKey)
        assertThat(transaction.type).isEqualTo(AccountTransactionType.DEBIT)
        assertThat(transaction.amount).isEqualTo(-10_000L)
        assertThat(transaction.otherBankCode).isEqualTo("BANK_B")
        assertThat(transaction.otherAccountNumber).isEqualTo("B-001")
    }

    @Test
    @Sql("/sql/transfer/S-102.sql")
    fun `S-102 잔액이 부족하면 송금 요청을 거절한다`() {
        // given
        val principal = CustomerPrincipal(1L, "customer", "password-hash")

        // when
        mockMvc
            .post("/account/A-001/transfer") {
                with(user(principal))
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "recipientBankCode": "BANK_B",
                        "recipientAccountNumber": "B-001",
                        "amount": 120000
                    }
                    """.trimIndent()
            }.andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("INSUFFICIENT_BALANCE") }
            }

        // then
        val account = accountRepository.findByAccountNumber("A-001")
        assertThat(account?.balance).isEqualTo(100_000L)
        assertThat(transferRepository.findAll()).isEmpty()
        assertThat(accountTransactionRepository.findAll()).isEmpty()
        settlementServer.verify(0, anyRequestedFor(anyUrl()))
    }

    @Test
    @Sql("/sql/transfer/S-103.sql")
    fun `S-103 중앙 결제거절 응답을 받으면 출금을 보상하고 송금을 실패 처리한다`() {
        // given
        val principal = CustomerPrincipal(1L, "customer", "password-hash")
        settlementServer.stubFor(
            WireMock
                .post(urlEqualTo("/settlements"))
                .withRequestBody(
                    equalToJson(
                        """
                        {
                            "sourceBankCode": "BANK_A",
                            "sourceAccountNumber": "A-001",
                            "recipientBankCode": "BANK_B",
                            "recipientAccountNumber": "B-001",
                            "amount": 10000
                        }
                        """.trimIndent(),
                        true,
                        true,
                    ),
                ).willReturn(
                    okJson(
                        """
                        {
                            "transferKey": "{{jsonPath request.body '$.transferKey'}}",
                            "state": "REJECTED"
                        }
                        """.trimIndent(),
                    ).withTransformers("response-template"),
                ),
        )

        // when
        val result =
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
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.state") { value("FAILED") }
                }

        // then
        val transfer = transferRepository.findAll().single()
        result.andExpect {
            jsonPath("$.transferKey") { value(transfer.transferKey) }
        }
        assertThat(transfer.state).isEqualTo(TransferState.FAILED)

        val account = accountRepository.findByAccountNumber("A-001")
        assertThat(account?.balance).isEqualTo(100_000L)

        val transactions = accountTransactionRepository.findAll()
        assertThat(transactions).hasSize(2)

        val debit = transactions.single { it.type == AccountTransactionType.DEBIT }
        assertThat(debit.accountId).isEqualTo(1L)
        assertThat(debit.transferKey).isEqualTo(transfer.transferKey)
        assertThat(debit.amount).isEqualTo(-10_000L)
        assertThat(debit.otherBankCode).isEqualTo("BANK_B")
        assertThat(debit.otherAccountNumber).isEqualTo("B-001")

        val compensation = transactions.single { it.type == AccountTransactionType.COMPENSATION }
        assertThat(compensation.accountId).isEqualTo(1L)
        assertThat(compensation.transferKey).isEqualTo(transfer.transferKey)
        assertThat(compensation.amount).isEqualTo(10_000L)
        assertThat(compensation.otherBankCode).isEqualTo("BANK_B")
        assertThat(compensation.otherAccountNumber).isEqualTo("B-001")
    }

    @Test
    @Sql("/sql/transfer/S-104.sql")
    fun `S-104 결제 요청이 도달하지 않으면 만료 후 출금을 보상하고 송금을 실패 처리한다`() {
        // given
        val principal = CustomerPrincipal(1L, "customer", "password-hash")
        settlementServer.stubFor(
            WireMock
                .get(urlPathEqualTo("/settlements:batchGetStates"))
                .withQueryParam("bankCode", equalTo("BANK_A"))
                .withQueryParam("transferKeys", matching(".+"))
                .willReturn(okJson("""{"states": {}}""")),
        )
        settlementServer.stop()

        // when
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
            }.andExpect {
                status { isOk() }
                jsonPath("$.state") { value("TRANSFERRING") }
            }

        val transfer = transferRepository.findAll().single()
        val afterExpiration = requireNotNull(transfer.createdAt).plusSeconds(301)
        settlementServer.start()
        doReturn(afterExpiration).whenever(clock).instant()
        Thread.sleep(1_000)

        // then
        val afterTransfer = transferRepository.findAll().single()
        assertThat(afterTransfer.state).isEqualTo(TransferState.FAILED)

        val afterAccount = accountRepository.findByAccountNumber("A-001")
        assertThat(afterAccount?.balance).isEqualTo(100_000L)

        val transactions = accountTransactionRepository.findAll()
        assertThat(transactions).hasSize(2)
        val recoveredDebit = transactions.single { it.type == AccountTransactionType.DEBIT }
        assertThat(recoveredDebit.amount).isEqualTo(-10_000L)

        val compensation = transactions.single { it.type == AccountTransactionType.COMPENSATION }
        assertThat(compensation.accountId).isEqualTo(1L)
        assertThat(compensation.transferKey).isEqualTo(transfer.transferKey)
        assertThat(compensation.amount).isEqualTo(10_000L)
        assertThat(compensation.otherBankCode).isEqualTo("BANK_B")
        assertThat(compensation.otherAccountNumber).isEqualTo("B-001")
    }

    class SettlementWireMockConfig : WireMockConfigurationCustomizer {
        override fun customize(
            configuration: WireMockConfiguration,
            options: ConfigureWireMock,
        ) {
            configuration.port(ServerSocket(0).use { it.localPort })
            // JDK HTTP 클라이언트의 chunked 요청에서 http2 전환 시 본문이 유실되는 문제 있음.
            // https://github.com/wiremock/wiremock/issues/3577
            configuration.http2PlainDisabled(true)
        }
    }
}
