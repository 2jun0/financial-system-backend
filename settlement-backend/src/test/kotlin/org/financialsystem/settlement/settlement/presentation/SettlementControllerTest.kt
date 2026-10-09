package org.financialsystem.settlement.settlement.presentation

import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.SettlementService
import org.financialsystem.settlement.settlement.application.dto.BatchGetSettlementStatesResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementRequest
import org.financialsystem.settlement.settlement.application.dto.SettlementResponse
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
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
            .post("/settlements") {
                contentType = MediaType.APPLICATION_JSON
                content =
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
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.transferKey") {
                    value("transfer-key")
                }
                jsonPath("$.state") {
                    value("RECIPIENT_VALIDATION_PENDING")
                }
            }
    }

    @Test
    fun `송금 키로 결제를 조회한다`() {
        // given
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

        // when & then
        mockMvc
            .get("/settlements/transfer-key") {
                param("sourceBankCode", "BANK_A")
            }.andExpect {
                status { isOk() }
                jsonPath("$.transferKey") {
                    value("transfer-key")
                }
                jsonPath("$.sourceBankCode") {
                    value("BANK_A")
                }
                jsonPath("$.sourceAccountNumber") {
                    value("source-account-number")
                }
                jsonPath("$.recipientBankCode") {
                    value("BANK_B")
                }
                jsonPath("$.recipientAccountNumber") {
                    value("recipient-account-number")
                }
                jsonPath("$.amount") {
                    value(10_000)
                }
                jsonPath("$.state") {
                    value("SETTLED")
                }
            }
    }

    @Nested
    inner class `결제 상태 배치 조회` {
        @Test
        fun `결제 상태를 여러개 조회한다`() {
            // given
            val transferKeys = arrayOf("transfer-key1", "transfer-key2", "transfer-key3")
            val serviceResponse =
                BatchGetSettlementStatesResponse(
                    states =
                        mapOf(
                            "transfer-key1" to SettlementState.REJECTED,
                            "transfer-key2" to SettlementState.SETTLED,
                            "transfer-key3" to SettlementState.RECIPIENT_VALIDATION_PENDING,
                        ),
                )
            given(settlementService.batchGetSettlementStates(any(), any())).willReturn(
                serviceResponse,
            )

            // when & then
            mockMvc
                .get("/settlements:batchGetStates") {
                    param("bankCode", "BANK_A")
                    param("transferKeys", *transferKeys)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.states['${transferKeys[0]}']") {
                        value(SettlementState.REJECTED.name)
                    }
                    jsonPath("$.states['${transferKeys[1]}']") {
                        value(SettlementState.SETTLED.name)
                    }
                    jsonPath("$.states['${transferKeys[2]}']") {
                        value(SettlementState.RECIPIENT_VALIDATION_PENDING.name)
                    }
                }
        }

        @ParameterizedTest
        @CsvSource(
            "0, 400",
            "1, 200",
            "100, 200",
            "101, 400",
        )
        fun `송금 키는 1~100 개만 조회 가능하다`(
            count: Int,
            expectedStatus: Int,
        ) {
            // given
            val transferKeys = Array(count) { "transfer-key-$it" }
            val serviceResponse =
                BatchGetSettlementStatesResponse(
                    states = transferKeys.associateWith { SettlementState.SETTLED },
                )
            given(settlementService.batchGetSettlementStates(any(), any())).willReturn(
                serviceResponse,
            )

            // then & when
            mockMvc
                .get("/settlements:batchGetStates") {
                    param("bankCode", "BANK_A")
                    param("transferKeys", *transferKeys)
                }.andExpect {
                    status { isEqualTo(expectedStatus) }
                }
        }
    }
}
