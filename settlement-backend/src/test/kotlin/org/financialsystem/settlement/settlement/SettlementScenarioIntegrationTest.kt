package org.financialsystem.settlement.settlement

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.noContent
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.financialsystem.settlement.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock
import org.wiremock.spring.InjectWireMock
import org.wiremock.spring.WireMockConfigurationCustomizer
import java.time.Clock
import java.time.Instant

@SpringBootTest
@IntegrationTest
@AutoConfigureMockMvc
@EnableWireMock(ConfigureWireMock(configurationCustomizers = [SettlementScenarioIntegrationTest.BankWireMockConfig::class]))
class SettlementScenarioIntegrationTest {
    @InjectWireMock
    lateinit var bankServer: WireMockServer

    @MockitoSpyBean
    lateinit var clock: Clock

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var settlementRepository: SettlementRepository

    @Autowired
    lateinit var bankRepository: BankRepository

    @BeforeEach
    fun setUp() {
        bankServer.resetAll()
        doReturn(Instant.parse("2026-10-24T00:00:00Z")).whenever(clock).instant()
        jdbcTemplate.update("UPDATE banks SET base_uri = ? WHERE bank_code = 'BANK_B'", bankServer.baseUrl())
    }

    @Test
    @Sql("/sql/settlement/S-201.sql")
    fun `S-201 수취 가능 응답을 받으면 결제잔액을 이동하고 결제를 완료한다`() {
        // given
        val validationRequest =
            """
            {
                "transferKey": "E-201",
                "sourceBankCode": "BANK_A",
                "sourceAccountNumber": "A-001",
                "recipientAccountNumber": "B-001",
                "amount": 10000,
                "transferCreatedAt": "2026-10-24T00:00:00Z"
            }
            """.trimIndent()
        bankServer.stubFor(
            WireMock
                .post(urlEqualTo("/settlement-validations"))
                .withRequestBody(equalToJson(validationRequest))
                .willReturn(okJson("""{"isAccepted": true}""")),
        )
        bankServer.stubFor(
            WireMock.post(urlEqualTo("/settlement-results/E-201/settled")).willReturn(noContent()),
        )

        // when
        mockMvc
            .post("/settlements") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "transferKey": "E-201",
                        "sourceBankCode": "BANK_A",
                        "sourceAccountNumber": "A-001",
                        "recipientBankCode": "BANK_B",
                        "recipientAccountNumber": "B-001",
                        "amount": 10000,
                        "transferCreatedAt": "2026-10-24T00:00:00Z"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.transferKey") { value("E-201") }
                jsonPath("$.state") { value("SETTLED") }
            }

        // then
        val settlement = settlementRepository.findAll().single()
        assertThat(settlement.transferKey).isEqualTo("E-201")
        assertThat(settlement.state).isEqualTo(SettlementState.SETTLED)
        assertThat(bankRepository.findByBankCode("BANK_A")?.balance).isEqualTo(990_000L)
        assertThat(bankRepository.findByBankCode("BANK_B")?.balance).isEqualTo(1_010_000L)
        bankServer.verify(WireMock.postRequestedFor(urlEqualTo("/settlement-results/E-201/settled")))
        bankServer.verify(
            1,
            WireMock
                .postRequestedFor(urlEqualTo("/settlement-validations"))
                .withRequestBody(equalToJson(validationRequest)),
        )
    }

    @Test
    @Sql("/sql/settlement/S-202.sql")
    fun `S-202 수취 계좌가 없으면 결제를 거절하고 결제잔액을 유지한다`() {
        // given
        val validationRequest =
            """
            {
                "transferKey": "E-202",
                "sourceBankCode": "BANK_A",
                "sourceAccountNumber": "A-001",
                "recipientAccountNumber": "B-404",
                "amount": 10000,
                "transferCreatedAt": "2026-10-24T00:00:00Z"
            }
            """.trimIndent()
        bankServer.stubFor(
            WireMock
                .post(urlEqualTo("/settlement-validations"))
                .withRequestBody(equalToJson(validationRequest))
                .willReturn(okJson("""{"isAccepted": false}""")),
        )
        bankServer.stubFor(
            WireMock.post(urlEqualTo("/settlement-results/E-202/rejected")).willReturn(noContent()),
        )

        // when
        mockMvc
            .post("/settlements") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "transferKey": "E-202",
                        "sourceBankCode": "BANK_A",
                        "sourceAccountNumber": "A-001",
                        "recipientBankCode": "BANK_B",
                        "recipientAccountNumber": "B-404",
                        "amount": 10000,
                        "transferCreatedAt": "2026-10-24T00:00:00Z"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.transferKey") { value("E-202") }
                jsonPath("$.state") { value("REJECTED") }
            }

        // then
        val settlement = settlementRepository.findAll().single()
        assertThat(settlement.transferKey).isEqualTo("E-202")
        assertThat(settlement.state).isEqualTo(SettlementState.REJECTED)
        assertThat(bankRepository.findByBankCode("BANK_A")?.balance).isEqualTo(1_000_000L)
        assertThat(bankRepository.findByBankCode("BANK_B")?.balance).isEqualTo(1_000_000L)
        bankServer.verify(WireMock.postRequestedFor(urlEqualTo("/settlement-results/E-202/rejected")))
        bankServer.verify(
            1,
            WireMock.postRequestedFor(urlEqualTo("/settlement-validations")).withRequestBody(equalToJson(validationRequest)),
        )
    }

    @Test
    @Sql("/sql/settlement/S-203.sql")
    fun `S-203 송금 은행의 결제잔액이 부족하면 결제를 거절하고 결제잔액을 유지한다`() {
        // given
        val validationRequest =
            """
            {
                "transferKey": "E-203",
                "sourceBankCode": "BANK_A",
                "sourceAccountNumber": "A-001",
                "recipientAccountNumber": "B-001",
                "amount": 10000,
                "transferCreatedAt": "2026-10-24T00:00:00Z"
            }
            """.trimIndent()
        bankServer.stubFor(
            WireMock
                .post(urlEqualTo("/settlement-validations"))
                .withRequestBody(equalToJson(validationRequest))
                .willReturn(okJson("""{"isAccepted": true}""")),
        )
        bankServer.stubFor(
            WireMock.post(urlEqualTo("/settlement-results/E-203/rejected")).willReturn(noContent()),
        )

        // when
        mockMvc
            .post("/settlements") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "transferKey": "E-203",
                        "sourceBankCode": "BANK_A",
                        "sourceAccountNumber": "A-001",
                        "recipientBankCode": "BANK_B",
                        "recipientAccountNumber": "B-001",
                        "amount": 10000,
                        "transferCreatedAt": "2026-10-24T00:00:00Z"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.transferKey") { value("E-203") }
                jsonPath("$.state") { value("REJECTED") }
            }

        // then
        val settlement = settlementRepository.findAll().single()
        assertThat(settlement.transferKey).isEqualTo("E-203")
        assertThat(settlement.state).isEqualTo(SettlementState.REJECTED)
        assertThat(bankRepository.findByBankCode("BANK_A")?.balance).isEqualTo(5_000L)
        assertThat(bankRepository.findByBankCode("BANK_B")?.balance).isEqualTo(1_000_000L)
        bankServer.verify(WireMock.postRequestedFor(urlEqualTo("/settlement-results/E-203/rejected")))
        bankServer.verify(
            1,
            WireMock.postRequestedFor(urlEqualTo("/settlement-validations")).withRequestBody(equalToJson(validationRequest)),
        )
    }

    @Test
    @Sql("/sql/settlement/S-204.sql")
    fun `S-204 수취 검증 요청이 타임아웃되면 결제를 거절하고 결제잔액을 유지한다`() {
        // given
        val validationRequest =
            """
            {
                "transferKey": "E-204",
                "sourceBankCode": "BANK_A",
                "sourceAccountNumber": "A-001",
                "recipientAccountNumber": "B-001",
                "amount": 10000,
                "transferCreatedAt": "2026-10-24T00:00:00Z"
            }
            """.trimIndent()
        bankServer.stubFor(
            WireMock
                .post(urlEqualTo("/settlement-validations"))
                .withRequestBody(equalToJson(validationRequest))
                .willReturn(okJson("""{"isAccepted": true}""").withFixedDelay(11_000)),
        )
        bankServer.stubFor(
            WireMock.post(urlEqualTo("/settlement-results/E-204/rejected")).willReturn(noContent()),
        )

        // when
        mockMvc
            .post("/settlements") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "transferKey": "E-204",
                        "sourceBankCode": "BANK_A",
                        "sourceAccountNumber": "A-001",
                        "recipientBankCode": "BANK_B",
                        "recipientAccountNumber": "B-001",
                        "amount": 10000,
                        "transferCreatedAt": "2026-10-24T00:00:00Z"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.transferKey") { value("E-204") }
                jsonPath("$.state") { value("REJECTED") }
            }

        // then
        val settlement = settlementRepository.findAll().single()
        assertThat(settlement.transferKey).isEqualTo("E-204")
        assertThat(settlement.state).isEqualTo(SettlementState.REJECTED)
        assertThat(bankRepository.findByBankCode("BANK_A")?.balance).isEqualTo(1_000_000L)
        assertThat(bankRepository.findByBankCode("BANK_B")?.balance).isEqualTo(1_000_000L)
        bankServer.verify(WireMock.postRequestedFor(urlEqualTo("/settlement-results/E-204/rejected")))
        bankServer.verify(
            1,
            WireMock.postRequestedFor(urlEqualTo("/settlement-validations")).withRequestBody(equalToJson(validationRequest)),
        )
    }

    class BankWireMockConfig : WireMockConfigurationCustomizer {
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
