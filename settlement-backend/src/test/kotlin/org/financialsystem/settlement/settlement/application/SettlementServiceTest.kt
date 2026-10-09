package org.financialsystem.settlement.settlement.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.BankFixtures
import org.financialsystem.SettlementFixtures
import org.financialsystem.settlement.bank.domain.Bank
import org.financialsystem.settlement.bank.exception.BankNotFoundException
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementRequest
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementResponse
import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.exception.SameBankSettlementException
import org.financialsystem.settlement.settlement.exception.SourceBankMismatchException
import org.financialsystem.settlement.settlement.exception.TransferKeyDuplicatedException
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.transaction.support.TransactionOperations
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SettlementServiceTest {
    val bankA = BankFixtures.bank(bankCode = "BANK_A", balance = 10_000L)
    val bankB = BankFixtures.bank(bankCode = "BANK_B", balance = 10_000L)
    val bankC = BankFixtures.bank(bankCode = "BANK_C", balance = 10_000L)

    val now = Instant.parse("2026-10-04T00:00:00Z")
    val clock = Clock.fixed(now, ZoneId.systemDefault())

    @Mock
    lateinit var settlementRepository: SettlementRepository

    @Mock
    lateinit var bankRepository: BankRepository

    @Mock
    lateinit var recipientBankClient: RecipientBankClient

    private lateinit var settlementService: SettlementService

    @BeforeEach
    fun setUp() {
        settlementService =
            SettlementService(
                clock,
                bankRepository,
                settlementRepository,
                recipientBankClient,
                TransactionOperations.withoutTransaction(),
            )
    }

    fun pendingSettlement(
        amount: Long,
        transferKey: String = "transferKey",
        sourceBank: Bank = bankA,
        recipientBank: Bank = bankB,
        sourceAccountNumber: String = "${sourceBank.bankCode}_ACCOUNT",
        recipientAccountNumber: String = "${recipientBank.bankCode}_ACCOUNT",
    ): Settlement =
        SettlementFixtures.settlement(
            transferKey = transferKey,
            sourceBankId = sourceBank.id!!,
            sourceAccountNumber = sourceAccountNumber,
            recipientBankId = recipientBank.id!!,
            recipientAccountNumber = recipientAccountNumber,
            amount = amount,
            transferCreatedAt = now,
        )

    @Nested
    inner class `결제를 조회한다` {
        @ParameterizedTest
        @EnumSource(SettlementState::class)
        fun `현재 상태를 포함한 상세 정보를 응답한다`(state: SettlementState) {
            // given
            val settlement = pendingSettlement(amount = 1_000, transferKey = "transfer-key")
            when (state) {
                SettlementState.RECIPIENT_VALIDATION_PENDING -> Unit
                SettlementState.SETTLED -> settlement.settle()
                SettlementState.REJECTED -> settlement.reject()
            }

            given(bankRepository.findById(bankA.id!!)).willReturn(Optional.of(bankA))
            given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
            given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)

            // when
            val response = settlementService.find(settlement.transferKey, bankA.bankCode)

            // then
            assertThat(response).isEqualTo(
                SettlementDetailResponse(
                    transferKey = settlement.transferKey,
                    sourceBankCode = bankA.bankCode,
                    sourceAccountNumber = settlement.sourceAccountNumber,
                    recipientBankCode = bankB.bankCode,
                    recipientAccountNumber = settlement.recipientAccountNumber,
                    amount = settlement.amount,
                    state = state,
                ),
            )
        }

        @Test
        fun `지정된 송금 은행이 아니면 예외를 던진다`() {
            // given
            val settlement = pendingSettlement(amount = 1_000, transferKey = "transfer-key")
            given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
            given(bankRepository.findById(bankA.id!!)).willReturn(Optional.of(bankA))

            // when
            assertThatThrownBy {
                settlementService.find(settlement.transferKey, bankC.bankCode)
            }
                // then
                .isInstanceOf(SourceBankMismatchException::class.java)
        }
    }

    @Nested
    inner class `결제를 요청한다` {
        private val settlementRequest =
            SettlementRequest(
                transferKey = "transferKey",
                sourceBankCode = bankA.bankCode,
                sourceAccountNumber = "BANK_A_ACCOUNT",
                recipientBankCode = bankB.bankCode,
                recipientAccountNumber = "BANK_B_ACCOUNT",
                amount = PositiveAmount(10_000L),
                transferCreatedAt = Instant.parse("2026-10-04T00:00:00Z"),
            )

        @Test
        fun `성공시 결제가 SETTLED 상태를 저장, 은행간 자금을 거래, 성공 결과를 통지한다`() {
            // given
            var savedSettlement: Settlement? = null

            given(bankRepository.findByBankCode(bankA.bankCode))
                .willReturn(bankA)
            given(bankRepository.findById(bankA.id!!)).willReturn(Optional.of(bankA))
            given(bankRepository.findByBankCode(bankB.bankCode))
                .willReturn(bankB)
            given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))

            given(settlementRepository.save(any<Settlement>())).willAnswer { invocation ->
                invocation.getArgument<Settlement>(0).also { savedSettlement = it }
            }
            given(settlementRepository.findByTransferKey(settlementRequest.transferKey))
                .willAnswer { savedSettlement }

            given(recipientBankClient.validateSettlement(any(), any()))
                .willReturn(ValidateSettlementResponse(true))

            // when
            val response = settlementService.requestSettlement(settlementRequest)

            // then
            verify(recipientBankClient)
                .notifySettledResult(bankB.baseUri, settlementRequest.transferKey)
            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(response.state).isEqualTo(SettlementState.SETTLED)
            assertThat(bankA.balance).isEqualTo(10_000L - settlementRequest.amount.value)
            assertThat(bankB.balance).isEqualTo(10_000L + settlementRequest.amount.value)
        }

        @Test
        fun `수취 은행이 거절한 경우 REJECTED 상태를 저장, 은행간 자금을 유지, 결과를 통지한다`() {
            // given
            var savedSettlement: Settlement? = null

            given(bankRepository.findByBankCode(bankA.bankCode))
                .willReturn(bankA)
            given(bankRepository.findByBankCode(bankB.bankCode))
                .willReturn(bankB)
            given(settlementRepository.save(any<Settlement>())).willAnswer { invocation ->
                invocation.getArgument<Settlement>(0).also { savedSettlement = it }
            }
            given(settlementRepository.findByTransferKey(settlementRequest.transferKey))
                .willAnswer { savedSettlement }
            given(recipientBankClient.validateSettlement(any(), any())).willReturn(ValidateSettlementResponse(false))

            // when
            val response = settlementService.requestSettlement(settlementRequest)

            // then
            verify(recipientBankClient)
                .notifyRejectedResult(bankB.baseUri, settlementRequest.transferKey)
            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(response.state).isEqualTo(SettlementState.REJECTED)
            assertThat(bankA.balance).isEqualTo(10_000L)
            assertThat(bankB.balance).isEqualTo(10_000L)
        }

        @Test
        fun `수취 검증 요청이 실패하면 결제를 거절(REJECTED)하고 통지한다`() {
            // given
            var savedSettlement: Settlement? = null

            given(bankRepository.findByBankCode(bankA.bankCode))
                .willReturn(bankA)
            given(bankRepository.findByBankCode(bankB.bankCode))
                .willReturn(bankB)
            given(settlementRepository.save(any<Settlement>())).willAnswer { invocation ->
                invocation.getArgument<Settlement>(0).also { savedSettlement = it }
            }
            given(settlementRepository.findByTransferKey(settlementRequest.transferKey))
                .willAnswer { savedSettlement }
            given(recipientBankClient.validateSettlement(any(), any()))
                .willThrow(RestClientException::class.java)

            // when
            val response = settlementService.requestSettlement(settlementRequest)

            // then
            assertThat(response.state).isEqualTo(SettlementState.REJECTED)
            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(bankA.balance).isEqualTo(10_000L)
            assertThat(bankB.balance).isEqualTo(10_000L)
            verify(recipientBankClient).notifyRejectedResult(bankB.baseUri, settlementRequest.transferKey)
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `완료된 결제를 재요청 한 경우 자금을 이동하지 않고 상태를 반환한다`(isSettled: Boolean) {
            // given
            val settlement = pendingSettlement(amount = settlementRequest.amount.value)
            if (isSettled) {
                settlement.settle()
            } else {
                settlement.reject()
            }

            given(settlementRepository.findByTransferKey(any())).willReturn(settlement)

            given(bankRepository.findByBankCode(bankA.bankCode))
                .willReturn(bankA)
            given(bankRepository.findByBankCode(bankB.bankCode))
                .willReturn(bankB)

            val expectedState = settlement.state

            // when
            val response = settlementService.requestSettlement(settlementRequest)

            // then
            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(response.state).isEqualTo(expectedState)
            assertThat(settlement.state).isEqualTo(expectedState)
            assertThat(bankA.balance).isEqualTo(10_000L)
            assertThat(bankB.balance).isEqualTo(10_000L)
        }

        @Test
        fun `동일 은행 간 요청은 예외를 던진다`() {
            // given
            val request =
                SettlementRequest(
                    transferKey = "transferKey",
                    sourceBankCode = "bankCode",
                    sourceAccountNumber = "BANK_A_ACCOUNT",
                    recipientBankCode = "bankCode",
                    recipientAccountNumber = "BANK_A_ACCOUNT",
                    amount = PositiveAmount(10_000),
                    transferCreatedAt = settlementRequest.transferCreatedAt,
                )

            // when
            assertThatThrownBy { settlementService.requestSettlement(request) }
                // then
                .isInstanceOf(SameBankSettlementException::class.java)
        }

        @Test
        fun `같은 송금 키의 기존 결제와 요청 내용이 다르면 예외를 던진다`() {
            // given
            val existed = pendingSettlement(amount = 20_000L)

            given(bankRepository.findByBankCode(bankA.bankCode))
                .willReturn(bankA)
            given(bankRepository.findByBankCode(bankB.bankCode))
                .willReturn(bankB)

            given(settlementRepository.findByTransferKey(any())).willReturn(existed)

            // when
            assertThatThrownBy { settlementService.requestSettlement(settlementRequest) }
                // then
                .isInstanceOf(
                    TransferKeyDuplicatedException::class.java,
                )
        }

        @Test
        fun `송금 은행이 존재하지 않으면 예외를 던진다`() {
            // given
            given(bankRepository.findByBankCode("BANK_A")).willReturn(null)

            // when
            assertThatThrownBy { settlementService.requestSettlement(settlementRequest) }
                // then
                .isInstanceOf(
                    BankNotFoundException::class.java,
                )
        }

        @Test
        fun `수취 은행이 존재하지 않으면 예외를 던진다`() {
            // given
            given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
            given(bankRepository.findByBankCode("BANK_B")).willReturn(null)

            // when
            assertThatThrownBy { settlementService.requestSettlement(settlementRequest) }
                // then
                .isInstanceOf(
                    BankNotFoundException::class.java,
                )
        }

        @Test
        fun `송금 은행의 잔액이 부족하면 결제를 거절한다`() {
            // given
            val bankA = BankFixtures.bank("BANK_A", 1L)
            var savedSettlement: Settlement? = null

            given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
            given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)
            given(bankRepository.findById(bankA.id!!)).willReturn(Optional.of(bankA))
            given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
            given(settlementRepository.save(any<Settlement>())).willAnswer { invocation ->
                invocation.getArgument<Settlement>(0).also { savedSettlement = it }
            }
            given(settlementRepository.findByTransferKey(settlementRequest.transferKey))
                .willAnswer { savedSettlement }
            given(recipientBankClient.validateSettlement(any(), any()))
                .willReturn(ValidateSettlementResponse(true))

            // when
            val response = settlementService.requestSettlement(settlementRequest)

            // then
            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(response.state).isEqualTo(SettlementState.REJECTED)
            assertThat(bankA.balance).isEqualTo(1L)
            assertThat(bankB.balance).isEqualTo(10_000L)
            verify(recipientBankClient)
                .notifyRejectedResult(bankB.baseUri, settlementRequest.transferKey)
        }

        @Test
        fun `결과 통지가 실패해도 확정된 결제 결과를 반환한다`() {
            // given
            var savedSettlement: Settlement? = null

            given(bankRepository.findByBankCode(bankA.bankCode))
                .willReturn(bankA)
            given(bankRepository.findById(bankA.id!!)).willReturn(Optional.of(bankA))
            given(bankRepository.findByBankCode(bankB.bankCode))
                .willReturn(bankB)
            given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))

            given(settlementRepository.save(any<Settlement>())).willAnswer { invocation ->
                invocation.getArgument<Settlement>(0).also { savedSettlement = it }
            }
            given(settlementRepository.findByTransferKey(settlementRequest.transferKey))
                .willAnswer { savedSettlement }

            given(recipientBankClient.validateSettlement(any(), any()))
                .willReturn(ValidateSettlementResponse(true))
            given(recipientBankClient.notifySettledResult(any(), any())).willThrow(RestClientException::class.java)

            // when
            val response = settlementService.requestSettlement(settlementRequest)

            // then
            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(response.state).isEqualTo(SettlementState.SETTLED)
        }

        @Test
        fun `만료된 요청인 경우엔 결제를 거절한다`() {
            // given
            val request =
                settlementRequest.copy(
                    transferCreatedAt =
                        settlementRequest.transferCreatedAt
                            .minus(SETTLEMENT_TIME_LIMIT),
                )
            given(settlementRepository.findByTransferKey(any())).willReturn(null)
            given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
            given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)

            // when
            val response = settlementService.requestSettlement(request)

            // then
            assertThat(response.transferKey).isEqualTo(request.transferKey)
            assertThat(response.state).isEqualTo(SettlementState.REJECTED)
            assertThat(bankA.balance).isEqualTo(10_000L)
            assertThat(bankB.balance).isEqualTo(10_000L)
            verifyNoInteractions(recipientBankClient)
        }

        @Test
        fun `수취 검증 후 만료되면 결제를 거절한다`() {
            // given
            var savedSettlement: Settlement? = null
            var currentTime = now
            val clock = mock<Clock>()
            val service =
                SettlementService(
                    clock,
                    bankRepository,
                    settlementRepository,
                    recipientBankClient,
                    TransactionOperations.withoutTransaction(),
                )
            given(clock.instant()).willAnswer { currentTime }

            given(settlementRepository.save(any<Settlement>())).willAnswer { invocation ->
                invocation.getArgument<Settlement>(0).also { savedSettlement = it }
            }
            given(settlementRepository.findByTransferKey(any()))
                .willAnswer { savedSettlement }
            given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
            given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)
            given(recipientBankClient.validateSettlement(any(), any()))
                .willAnswer {
                    // 만료시킴
                    currentTime = now.plus(SETTLEMENT_TIME_LIMIT)
                    ValidateSettlementResponse(true)
                }

            // when
            val response = service.requestSettlement(settlementRequest)

            // then
            assertThat(response.transferKey).isEqualTo(settlementRequest.transferKey)
            assertThat(response.state).isEqualTo(SettlementState.REJECTED)
            assertThat(bankA.balance).isEqualTo(10_000L)
            assertThat(bankB.balance).isEqualTo(10_000L)
            verify(recipientBankClient)
                .notifyRejectedResult(bankB.baseUri, settlementRequest.transferKey)
        }
    }
}
