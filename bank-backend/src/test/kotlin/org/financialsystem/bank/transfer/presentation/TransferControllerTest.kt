package org.financialsystem.bank.transfer.presentation

import org.financialsystem.bank.config.SecurityConfig
import org.financialsystem.bank.customer.application.CustomerPrincipal
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.transfer.application.TransferService
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.domain.Transfer
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(TransferController::class)
@Import(SecurityConfig::class)
class TransferControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var transferService: TransferService

    @Test
    fun `소수(float) 금액은 송금이 거절된다`() {
        val principal = CustomerPrincipal(1L, "loginId", "passwordHash")

        mockMvc
            .perform(
                post("/account/001-source-account/transfer")
                    .with(user(principal))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "recipientBankCode": "BANK_CODE",
                            "recipientAccountNumber": "recipient-account-number",
                            "amount": 10000.5
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)

        verifyNoInteractions(transferService)
    }

    @Test
    fun `송금 결과로 OK, 송금key를 응답한다`() {
        val principal = CustomerPrincipal(1L, "loginId", "passwordHash")
        val accountId = 10L
        val accountNumber = "001-source-account"
        val transferRequest =
            TransferRequest(
                "BANK_B",
                "recipient-account-number",
                PositiveAmount(10_000L),
            )
        val transfer =
            Transfer.initiate(
                "transferKey",
                accountId,
                transferRequest.recipientBankCode,
                transferRequest.recipientAccountNumber,
                transferRequest.amount,
            )
        given(transferService.initiateTransfer(principal.customerId, accountNumber, transferRequest))
            .willReturn(transfer)

        mockMvc
            .perform(
                post("/account/$accountNumber/transfer")
                    .with(user(principal))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "recipientBankCode": "${transferRequest.recipientBankCode}",
                            "recipientAccountNumber": "${transferRequest.recipientAccountNumber}",
                            "amount": ${transferRequest.amount.value}
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.transferKey").value(transfer.transferKey))
    }
}
