package org.financialsystem.settlement.settlement.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.settlement.bank.domain.Bank
import org.financialsystem.settlement.bank.exception.BankNotFoundException
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.exception.TransferKeyDuplicatedException
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class SettlementServiceTest {
    @Mock
    lateinit var settlementRepository: SettlementRepository

    @Mock
    lateinit var bankRepository: BankRepository

    @Test
    fun `은행 간 결제를 개시하면 settlement 가 저장된다`() {
        val initiateSettlementRequest =
            InitiateSettlementRequest(
                "transferKey",
                "BANK_A",
                "BANK_A_ACCOUNT",
                "BANK_B",
                "BANK_B_ACCOUNT",
                PositiveAmount(10_000),
            )
        val bankA = Bank.create("BANK_A")
        ReflectionTestUtils.setField(bankA, "id", 1L)
        val bankB = Bank.create("BANK_B")
        ReflectionTestUtils.setField(bankB, "id", 2L)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)
        val settlementService = SettlementService(bankRepository, settlementRepository)

        val response = settlementService.initiate(initiateSettlementRequest)

        val captor = ArgumentCaptor.forClass(Settlement::class.java)

        verify(settlementRepository).save(captor.capture())
        val savedSettlement = captor.value
        assertThat(savedSettlement.transferKey)
            .isEqualTo(initiateSettlementRequest.transferKey)
        assertThat(savedSettlement.sourceBankId).isEqualTo(1L)
        assertThat(savedSettlement.recipientBankId).isEqualTo(2L)
        assertThat(savedSettlement.amount)
            .isEqualTo(initiateSettlementRequest.amount.value)
        assertThat(savedSettlement.state)
            .isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
        assertThat(response.transferKey).isEqualTo("transferKey")
        assertThat(response.state).isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
    }

    @Test
    fun `은행 간 결제를 개시할 때 송신 은행이 존재하지 않으면 예외를 던진다`() {
        val initiateSettlementRequest =
            InitiateSettlementRequest(
                "transferKey",
                "BANK_A",
                "BANK_A_ACCOUNT",
                "BANK_B",
                "BANK_B_ACCOUNT",
                PositiveAmount(10_000),
            )
        val bankB = Bank.create("BANK_B")
        ReflectionTestUtils.setField(bankB, "id", 2L)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(null)
        val settlementService = SettlementService(bankRepository, settlementRepository)

        assertThatThrownBy { settlementService.initiate(initiateSettlementRequest) }.isInstanceOf(
            BankNotFoundException::class.java,
        )
    }

    @Test
    fun `은행 간 결제를 개시할 때 수취 은행이 존재하지 않으면 예외를 던진다`() {
        val initiateSettlementRequest =
            InitiateSettlementRequest(
                "transferKey",
                "BANK_A",
                "BANK_A_ACCOUNT",
                "BANK_B",
                "BANK_B_ACCOUNT",
                PositiveAmount(10_000),
            )
        val bankA = Bank.create("BANK_A")
        ReflectionTestUtils.setField(bankA, "id", 2L)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(null)
        val settlementService = SettlementService(bankRepository, settlementRepository)

        assertThatThrownBy { settlementService.initiate(initiateSettlementRequest) }.isInstanceOf(
            BankNotFoundException::class.java,
        )
    }

    @Test
    fun `동일한 결제 개시 요청은 새로 생성하지 않고 기존의 결제를 반환한다`() {
        val initiateSettlementRequest =
            InitiateSettlementRequest(
                "transferKey",
                "BANK_A",
                "BANK_A_ACCOUNT",
                "BANK_B",
                "BANK_B_ACCOUNT",
                PositiveAmount(10_000),
            )
        val bankA = Bank.create("BANK_A")
        ReflectionTestUtils.setField(bankA, "id", 1L)
        val bankB = Bank.create("BANK_B")
        ReflectionTestUtils.setField(bankB, "id", 2L)
        val existingSettlement =
            Settlement
                .initiate(
                    "transferKey",
                    1L,
                    "BANK_A_ACCOUNT",
                    2L,
                    "BANK_B_ACCOUNT",
                    PositiveAmount(10_000),
                )
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)
        given(settlementRepository.findByTransferKey("transferKey")).willReturn(existingSettlement)
        val settlementService = SettlementService(bankRepository, settlementRepository)

        val response = settlementService.initiate(initiateSettlementRequest)

        assertThat(response.transferKey).isEqualTo("transferKey")
        assertThat(response.state).isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
    }

    @Test
    fun `같은 송금 키로 다른 결제를 개시하면 예외를 던진다`() {
        val initiateSettlementRequest =
            InitiateSettlementRequest(
                "transferKey",
                "BANK_A",
                "BANK_A_ACCOUNT",
                "BANK_B",
                "BANK_B_ACCOUNT",
                PositiveAmount(20_000),
            )
        val bankA = Bank.create("BANK_A")
        ReflectionTestUtils.setField(bankA, "id", 1L)
        val bankB = Bank.create("BANK_B")
        ReflectionTestUtils.setField(bankB, "id", 2L)
        val existingSettlement =
            Settlement.initiate(
                "transferKey",
                1L,
                "BANK_A_ACCOUNT",
                2L,
                "BANK_B_ACCOUNT",
                PositiveAmount(10_000),
            )
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)
        given(settlementRepository.findByTransferKey("transferKey")).willReturn(existingSettlement)
        val settlementService = SettlementService(bankRepository, settlementRepository)

        assertThatThrownBy { settlementService.initiate(initiateSettlementRequest) }.isInstanceOf(
            TransferKeyDuplicatedException::class.java,
        )
    }

    @Test
    fun `수취 검증 대기 상태의 결제를 조회한다`() {
        val bankA = Bank.create("BANK_A")
        ReflectionTestUtils.setField(bankA, "id", 1L)
        val bankB = Bank.create("BANK_B")
        ReflectionTestUtils.setField(bankB, "id", 2L)
        val bankC = Bank.create("BANK_C")
        ReflectionTestUtils.setField(bankC, "id", 3L)
        val pendingSettlements =
            mutableListOf(
                Settlement.initiate(
                    "transferKey-1",
                    2L,
                    "BANK_B_ACCOUNT",
                    1L,
                    "BANK_A_ACCOUNT",
                    PositiveAmount(10_000),
                ),
                Settlement.initiate(
                    "transferKey-2",
                    3L,
                    "BANK_C_ACCOUNT",
                    1L,
                    "BANK_A_ACCOUNT",
                    PositiveAmount(20_000),
                ),
            )
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(
            settlementRepository.findByRecipientBankIdAndState(
                1L,
                SettlementState.RECIPIENT_VALIDATION_PENDING,
                PageRequest.of(0, 100, Sort.by("createdAt", "id").ascending()),
            ),
        ).willReturn(pendingSettlements)
        given(bankRepository.findAllById(setOf(2L, 3L))).willReturn(listOf(bankB, bankC))
        val settlementService = SettlementService(bankRepository, settlementRepository)

        val responses = settlementService.findPendingSettlements("BANK_A")

        assertThat(responses).containsExactly(
            PendingSettlementResponse("transferKey-1", "BANK_B", "BANK_B_ACCOUNT", "BANK_A", "BANK_A_ACCOUNT", 10_000),
            PendingSettlementResponse("transferKey-2", "BANK_C", "BANK_C_ACCOUNT", "BANK_A", "BANK_A_ACCOUNT", 20_000),
        )
    }

    @Test
    fun `수취 검증 대기 상태의 결제를 조회할 때 수취 은행이 존재하지 않으면 예외를 던진다`() {
        given(bankRepository.findByBankCode("BANK_A")).willReturn(null)
        val settlementService = SettlementService(bankRepository, settlementRepository)

        assertThatThrownBy { settlementService.findPendingSettlements("BANK_A") }
            .isInstanceOf(BankNotFoundException::class.java)
        verifyNoInteractions(settlementRepository)
    }
}
