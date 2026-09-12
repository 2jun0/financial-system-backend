package org.financialsystem.settlement.settlement.presentation

import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.SettlementService
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementResponse
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SettlementController::class)
class SettlementControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var settlementService: SettlementService

    @Test
    fun `결제 개시 결과로 송금 키와 현재 상태를 응답한다`() {
        val request =
            InitiateSettlementRequest(
                transferKey = "transfer-key",
                sourceBankCode = "BANK_A",
                sourceAccountNumber = "source-account-number",
                recipientBankCode = "BANK_B",
                recipientAccountNumber = "recipient-account-number",
                amount = PositiveAmount(10_000),
            )
        given(settlementService.initiate(request)).willReturn(
            InitiateSettlementResponse(
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
                            "amount": 10000
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.transferKey").value("transfer-key"))
            .andExpect(jsonPath("$.state").value("RECIPIENT_VALIDATION_PENDING"))
    }
}
