package org.financialsystem.bank.settlement.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.IOException
import java.time.Instant

class RestSettlementClientTest {
    private val baseUrl = "http://settlement.test"
    private val request =
        SettlementRequest(
            transferKey = "transfer_key",
            sourceBankCode = "BANK_A",
            sourceAccountNumber = "001-source-account",
            recipientBankCode = "BANK_B",
            recipientAccountNumber = "recipient-account-number",
            amount = PositiveAmount(10_000L),
            transferCreatedAt = Instant.parse("2026-10-04T00:00:00Z"),
        )
    private lateinit var server: MockRestServiceServer
    private lateinit var settlementClient: RestSettlementClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        settlementClient = RestSettlementClient(builder, baseUrl)
    }

    @Test
    fun `결제 요청이 성공하면 결제 결과를 반환한다`() {
        server
            .expect(requestTo("$baseUrl/settlements"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"transferKey":"transfer_key","state":"SETTLED"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = settlementClient.requestSettlement(request)

        assertThat(response.transferKey).isEqualTo("transfer_key")
        assertThat(response.state).isEqualTo(SettlementState.SETTLED)
        server.verify()
    }

    @Test
    fun `성공 응답에 본문이 없으면 결제 요청 실패 예외로 변환한다`() {
        server
            .expect(requestTo("$baseUrl/settlements"))
            .andRespond(withSuccess())

        assertThatThrownBy { settlementClient.requestSettlement(request) }
            .isInstanceOf(SettlementClientException::class.java)
    }

    @Test
    fun `중앙 결제 서버에 연결하지 못하면 결제 요청 실패 예외로 변환한다`() {
        server
            .expect(requestTo("$baseUrl/settlements"))
            .andRespond(withException(IOException("connection refused")))

        assertThatThrownBy { settlementClient.requestSettlement(request) }
            .isInstanceOf(SettlementClientException::class.java)
            .hasCauseInstanceOf(ResourceAccessException::class.java)
    }

    @Test
    fun `중앙 결제 서버가 4xx 를 응답하면 결제 요청 실패 예외로 변환한다`() {
        server
            .expect(requestTo("$baseUrl/settlements"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertThatThrownBy { settlementClient.requestSettlement(request) }
            .isInstanceOf(SettlementClientException::class.java)
            .hasCauseInstanceOf(HttpClientErrorException::class.java)
    }

    @Test
    fun `중앙 결제 서버가 5xx 를 응답하면 결제 요청 실패 예외로 변환한다`() {
        server
            .expect(requestTo("$baseUrl/settlements"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { settlementClient.requestSettlement(request) }
            .isInstanceOf(SettlementClientException::class.java)
            .hasCauseInstanceOf(HttpServerErrorException::class.java)
    }
}
