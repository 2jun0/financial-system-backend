package org.financialsystem.bank.settlement.presentation

import org.financialsystem.bank.config.SecurityConfig
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.SettlementResultService
import org.financialsystem.bank.settlement.application.SettlementValidationService
import org.financialsystem.bank.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.bank.settlement.application.dto.ValidateSettlementResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

@WebMvcTest(SettlementController::class)
@Import(SecurityConfig::class)
class SettlementControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var settlementResultService: SettlementResultService

    @MockitoBean
    lateinit var settlementValidationService: SettlementValidationService

    private val transferKey = "transfer-key"

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `수취 검증 요청을 받으면 허용 여부를 응답한다`(isAccepted: Boolean) {
        val request =
            ValidateSettlementRequest(
                transferKey = transferKey,
                sourceBankCode = "BANK_A",
                sourceAccountNumber = "BANK_A_ACCOUNT",
                recipientAccountNumber = "BANK_B_ACCOUNT",
                amount = PositiveAmount(10_000),
            )

        given(settlementValidationService.validateSettlement(request))
            .willReturn(ValidateSettlementResponse(isAccepted))

        mockMvc
            .post("/settlement-validations") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.isAccepted") {
                    value(isAccepted)
                }
            }
    }

    @Test
    fun `수취 검증 요청의 송금 키가 비어 있으면 잘못된 요청을 응답한다`() {
        val request =
            ValidateSettlementRequest(
                transferKey = " ",
                sourceBankCode = "BANK_A",
                sourceAccountNumber = "BANK_A_ACCOUNT",
                recipientAccountNumber = "BANK_B_ACCOUNT",
                amount = PositiveAmount(10_000),
            )

        mockMvc
            .post("/settlement-validations") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `결제완료 결과 통지를 받으면 수취 완료 결과를 반영한다`() {
        mockMvc
            .post("/settlement-results/$transferKey/settled")
            .andExpect { status { isNoContent() } }

        verify(settlementResultService).applySettledResult(transferKey)
    }

    @Test
    fun `결제거절 결과 통지를 받으면 수취 거절 결과를 반영한다`() {
        mockMvc
            .post("/settlement-results/$transferKey/rejected")
            .andExpect { status { isNoContent() } }

        verify(settlementResultService).applyRejectedResult(transferKey)
    }
}
