package org.financialsystem.settlement.settlement.presentation

import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.SettlementService
import org.financialsystem.settlement.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementRequest
import org.financialsystem.settlement.settlement.application.dto.SettlementResponse
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(SettlementController::class)
class SettlementControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var settlementService: SettlementService

    @Test
    fun `결제 요청 결과로 송금 키와 현재 상태를 응답한다`() {
        val request =
            SettlementRequest(
                transferKey = "transfer-key",
                sourceBankCode = "BANK_A",
                sourceAccountNumber = "source-account-number",
                recipientBankCode = "BANK_B",
                recipientAccountNumber = "recipient-account-number",
                amount = PositiveAmount(10_000),
                transferCreatedAt = Instant.parse("2026-10-04T00:00:00Z"),
            )
        given(settlementService.requestSettlement(request)).willReturn(
            SettlementResponse(
                transferKey = "transfer-key",
                state = SettlementState.RECIPIENT_VALIDATION_PENDING,
            ),
        )

        mockMvc
            .perform(
                post("/settlements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "transferKey": "transfer-key",
                            "sourceBankCode": "BANK_A",
                            "sourceAccountNumber": "source-account-number",
                            "recipientBankCode": "BANK_B",
                            "recipientAccountNumber": "recipient-account-number",
                            "amount": 10000,
                            "transferCreatedAt": "${request.transferCreatedAt}"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.transferKey").value("transfer-key"))
            .andExpect(jsonPath("$.state").value("RECIPIENT_VALIDATION_PENDING"))
    }

    @Test
    fun `송금 은행이 송금 키로 결제를 조회한다`() {
        given(settlementService.find("transfer-key", "BANK_A")).willReturn(
            SettlementDetailResponse(
                transferKey = "transfer-key",
                sourceBankCode = "BANK_A",
                sourceAccountNumber = "source-account-number",
                recipientBankCode = "BANK_B",
                recipientAccountNumber = "recipient-account-number",
                amount = 10_000,
                state = SettlementState.SETTLED,
            ),
        )

        mockMvc
            .perform(
                get("/settlements/transfer-key")
                    .queryParam("sourceBankCode", "BANK_A"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.transferKey").value("transfer-key"))
            .andExpect(jsonPath("$.sourceBankCode").value("BANK_A"))
            .andExpect(jsonPath("$.sourceAccountNumber").value("source-account-number"))
            .andExpect(jsonPath("$.recipientBankCode").value("BANK_B"))
            .andExpect(jsonPath("$.recipientAccountNumber").value("recipient-account-number"))
            .andExpect(jsonPath("$.amount").value(10_000))
            .andExpect(jsonPath("$.state").value("SETTLED"))
    }
}
