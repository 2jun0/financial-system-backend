package org.financialsystem.settlement.settlement.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.settlement.settlement.application.exception.RecipientBankClientException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException
import java.time.Instant
import kotlin.test.assertFailsWith

class RestRecipientBankClientTest {
    private val bankUri = "http://bank-b.test"
    private val transferKey = "transfer-key"
    private val request =
        ValidateSettlementRequest(
            transferKey = transferKey,
            sourceBankCode = "BANK_A",
            sourceAccountNumber = "source-account",
            recipientAccountNumber = "recipient-account",
            amount = PositiveAmount(10_000L),
            transferCreatedAt = Instant.parse("2026-10-04T00:00:00Z"),
        )
    private lateinit var server: MockRestServiceServer
    private lateinit var recipientBankClient: RestRecipientBankClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        recipientBankClient = RestRecipientBankClient(builder)
    }

    @Nested
    inner class `수취 검증` {
        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `수취 은행의 검증 결과를 반환한다`(isAccepted: Boolean) {
            // given
            server
                .expect(requestTo("$bankUri/settlement-validations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""{"isAccepted":$isAccepted}""", MediaType.APPLICATION_JSON))

            // when
            val response = recipientBankClient.validateSettlement(bankUri, request)

            // then
            assertThat(response.isAccepted).isEqualTo(isAccepted)
            server.verify()
        }

        @Test
        fun `검증 응답 본문이 없으면 전용 예외를 던진다`() {
            // given
            server
                .expect(requestTo("$bankUri/settlement-validations"))
                .andRespond(withSuccess())

            // when & then
            assertFailsWith<RecipientBankClientException> {
                recipientBankClient.validateSettlement(bankUri, request)
            }
            server.verify()
        }

        @Test
        fun `통신 실패를 전용 예외로 감싸고 원인을 보존한다`() {
            // given
            val cause = IOException("connection refused")
            server
                .expect(requestTo("$bankUri/settlement-validations"))
                .andRespond(withException(cause))

            // when
            val exception =
                assertFailsWith<RecipientBankClientException> {
                    recipientBankClient.validateSettlement(bankUri, request)
                }

            // then
            assertThat(exception).rootCause().isSameAs(cause)
            server.verify()
        }

        @ParameterizedTest
        @EnumSource(value = HttpStatus::class, names = ["BAD_REQUEST", "INTERNAL_SERVER_ERROR"])
        fun `HTTP 오류를 전용 예외로 감싼다`(status: HttpStatus) {
            // given
            server
                .expect(requestTo("$bankUri/settlement-validations"))
                .andRespond(withStatus(status))

            // when
            val exception =
                assertFailsWith<RecipientBankClientException> {
                    recipientBankClient.validateSettlement(bankUri, request)
                }

            // then
            assertThat(exception.cause).isNotNull()
            server.verify()
        }

        @Test
        fun `검증 응답 해석 실패를 전용 예외로 감싼다`() {
            // given
            server
                .expect(requestTo("$bankUri/settlement-validations"))
                .andRespond(withSuccess("""{"isAccepted":{}}""", MediaType.APPLICATION_JSON))

            // when
            val exception =
                assertFailsWith<RecipientBankClientException> {
                    recipientBankClient.validateSettlement(bankUri, request)
                }

            // then
            assertThat(exception.cause).isNotNull()
            server.verify()
        }

        @Test
        fun `통신과 무관한 예외는 감싸지 않는다`() {
            // given
            val cause = IllegalStateException("unexpected failure")
            server
                .expect(requestTo("$bankUri/settlement-validations"))
                .andRespond { throw cause }

            // when
            val exception =
                assertFailsWith<IllegalStateException> {
                    recipientBankClient.validateSettlement(bankUri, request)
                }

            // then
            assertThat(exception).isSameAs(cause)
            server.verify()
        }
    }

    @Nested
    inner class `결제완료 결과 통지` {
        @Test
        fun `수취 은행에 결제완료 결과를 통지한다`() {
            // given
            server
                .expect(requestTo("$bankUri/settlement-results/$transferKey/settled"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess())

            // when
            recipientBankClient.notifySettledResult(bankUri, transferKey)

            // then
            server.verify()
        }

        @ParameterizedTest
        @EnumSource(value = HttpStatus::class, names = ["BAD_REQUEST", "INTERNAL_SERVER_ERROR"])
        fun `완료 통지 실패를 전용 예외로 감싼다`(status: HttpStatus) {
            // given
            server
                .expect(requestTo("$bankUri/settlement-results/$transferKey/settled"))
                .andRespond(withStatus(status))

            // when
            val exception =
                assertFailsWith<RecipientBankClientException> {
                    recipientBankClient.notifySettledResult(bankUri, transferKey)
                }

            // then
            assertThat(exception.cause).isNotNull()
            server.verify()
        }
    }

    @Nested
    inner class `결제거절 결과 통지` {
        @Test
        fun `수취 은행에 결제거절 결과를 통지한다`() {
            // given
            server
                .expect(requestTo("$bankUri/settlement-results/$transferKey/rejected"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess())

            // when
            recipientBankClient.notifyRejectedResult(bankUri, transferKey)

            // then
            server.verify()
        }

        @ParameterizedTest
        @EnumSource(value = HttpStatus::class, names = ["BAD_REQUEST", "INTERNAL_SERVER_ERROR"])
        fun `거절 통지 실패를 전용 예외로 감싼다`(status: HttpStatus) {
            // given
            server
                .expect(requestTo("$bankUri/settlement-results/$transferKey/rejected"))
                .andRespond(withStatus(status))

            // when
            val exception =
                assertFailsWith<RecipientBankClientException> {
                    recipientBankClient.notifyRejectedResult(bankUri, transferKey)
                }

            // then
            assertThat(exception.cause).isNotNull()
            server.verify()
        }
    }
}
